package com.publicfund.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

@Service
public class FileStorageService {
    private final Path root;
    public FileStorageService(@org.springframework.beans.factory.annotation.Value("${app.upload-root:uploads}") String directory) { root=Paths.get(directory).toAbsolutePath().normalize(); }
    public String storeEvidenceFile(MultipartFile file) { return store(file, "evidence", true); }
    public String storeReconciliationFile(MultipartFile file) { return store(file, "reconciliation", false); }
    public String storePaymentProof(MultipartFile file) { return store(file, "payments", false); }

    public String validate(MultipartFile file, boolean allowDocx) {
        if (file==null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload.");
        if (file.getSize()>=25L*1024*1024) throw new IllegalArgumentException("The uploaded content must be less than 25 MB.");
        String name=displayName(file);
        String ext=name.substring(Math.max(0,name.lastIndexOf('.')+1)).toLowerCase(Locale.ROOT);
        Map<String,String> types=Map.of("pdf","application/pdf","png","image/png","jpg","image/jpeg","jpeg","image/jpeg",
            "docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (!types.containsKey(ext) || (!allowDocx && ext.equals("docx")))
            throw new IllegalArgumentException(allowDocx ? "Only PDF, JPG, PNG and DOCX files are accepted." : "Only PDF, JPG and PNG files are accepted.");
        try (InputStream in=file.getInputStream()) {
            byte[] head=in.readNBytes(8);
            boolean valid=switch(ext) {
                case "pdf" -> head.length>=5 && new String(head,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
                case "png" -> Arrays.equals(head,new byte[]{(byte)137,80,78,71,13,10,26,10});
                case "jpg","jpeg" -> head.length>=3 && (head[0]&255)==255 && (head[1]&255)==216 && (head[2]&255)==255;
                case "docx" -> head.length>=4 && head[0]==80 && head[1]==75 && head[2]==3 && head[3]==4;
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("The file contents do not match its type. Upload a valid document or image.");
            if (ext.equals("docx")) validateDocx(file);
            if (Set.of("png","jpg","jpeg").contains(ext)) {
                try (var source=file.getInputStream(); var image=ImageIO.createImageInputStream(source)) {
                    var readers=ImageIO.getImageReaders(image);
                    if (!readers.hasNext()) throw new IllegalArgumentException("This image is invalid.");
                    var reader=readers.next();
                    try {
                        reader.setInput(image);
                        long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                        if(pixels<=0 || pixels>40_000_000) throw new IllegalArgumentException("Image dimensions are too large. Use an image under 40 megapixels.");
                    } finally { reader.dispose(); }
                }
            }
            return types.get(ext);
        } catch (IOException e) { throw new IllegalArgumentException("The file could not be read. Choose a valid file and try again."); }
    }
    private void validateDocx(MultipartFile file) throws IOException {
        boolean content=false,document=false; long total=0; int entries=0;
        try(var zip=new ZipInputStream(file.getInputStream())) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                if(++entries>1000 || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))
                    throw new IllegalArgumentException("This document is not supported. Upload a standard DOCX without macros.");
                content|=entry.getName().equals("[Content_Types].xml");
                document|=entry.getName().equals("word/document.xml");
                byte[] buffer=new byte[8192]; int n;
                while((n=zip.read(buffer))!=-1) {
                    total+=n;
                    if(total>50L*1024*1024) throw new IllegalArgumentException("The expanded document is too large.");
                }
            }
        }
        if(!content || !document) throw new IllegalArgumentException("This is not a valid DOCX document.");
    }
    public String displayName(MultipartFile file) {
        String raw=file.getOriginalFilename();
        if(raw==null || raw.isBlank()) throw new IllegalArgumentException("File name is required.");
        String name=raw.replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
        if(name.isBlank() || name.length()>200) throw new IllegalArgumentException("Use a filename between 1 and 200 characters.");
        return name;
    }
    private String store(MultipartFile file, String directory, boolean docx) {
        String type=validate(file,docx);
        String ext=switch(type) { case "application/pdf" -> ".pdf"; case "image/png" -> ".png"; case "image/jpeg" -> ".jpg"; default -> ".docx"; };
        Path destination=root.resolve(directory).resolve(UUID.randomUUID()+ext).normalize();
        try {
            Files.createDirectories(destination.getParent());
            try(InputStream input=file.getInputStream()) { Files.copy(input,destination); }
            if(TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if(status!=STATUS_COMMITTED) {
                            try { Files.deleteIfExists(destination); } catch(IOException ignored) { /* safe orphan; never claim submission succeeded */ }
                        }
                    }
                });
            return destination.toString();
        } catch(IOException e) {
            try { Files.deleteIfExists(destination); } catch(IOException ignored) {}
            throw new IllegalArgumentException("Upload failed. Your file was not stored; please try again.");
        }
    }
    public Resource read(String path) {
        if(path==null) throw new IllegalArgumentException("No file has been uploaded.");
        Path target=Paths.get(path).toAbsolutePath().normalize();
        try {
            if(!target.startsWith(root) || !target.toRealPath().startsWith(root.toRealPath()) || !Files.isRegularFile(target))
                throw new IllegalArgumentException("The file is unavailable. Please contact the system owner.");
            return new UrlResource(target.toUri());
        } catch(IOException e) { throw new IllegalArgumentException("The file is unavailable. Please contact the system owner."); }
    }
}
