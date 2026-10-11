import { ArrowRight, ClipboardText, Bell, Check, SquaresFour, FileText, Eye, EyeSlash, FolderOpen, SignOut, EnvelopeSimple, Bank, Plus, MagnifyingGlass, ShieldCheck, UploadSimple, User, Moon, Sun, List, ArrowUpRight, Clock, CheckCircle, Question, X } from "@phosphor-icons/react";

const icons = { arrowRight:ArrowRight, audit:ClipboardText, bell:Bell, check:Check, dashboard:SquaresFour, document:FileText, eye:Eye, eyeOff:EyeSlash, folder:FolderOpen, logout:SignOut, mail:EnvelopeSimple, money:Bank, plus:Plus, search:MagnifyingGlass, shield:ShieldCheck, upload:UploadSimple, user:User, moon:Moon, sun:Sun, menu:List, external:ArrowUpRight, clock:Clock, complete:CheckCircle, help:Question, close:X };
export default function Icon({name,size=20,className=""}) {
  const Glyph = icons[name] || SquaresFour;
  return <Glyph size={size} weight="regular" className={className} aria-hidden="true" />;
}
