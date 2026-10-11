param([switch]$ConfigureMail)
$ErrorActionPreference = 'Stop'
$secretDirectory = Join-Path $PSScriptRoot '.local'
$secretPath = Join-Path $secretDirectory 'mail-password.xml'
if ($ConfigureMail) {
    Write-Host 'Create a NEW Google App Password named Public Fund System Mail.'
    Write-Host 'Paste it into the private prompt below, not into chat. Input will be hidden.'
    $mailSecret = Read-Host 'New Google App Password' -AsSecureString
    if ($mailSecret.Length -eq 0) { throw 'No app password entered; nothing saved.' }
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    # Windows DPAPI binds the encrypted secret to this Windows user on this computer.
    $mailSecret | Export-Clixml -LiteralPath $secretPath
    $mailSecret.Dispose()
    Write-Host 'Mail credential saved encrypted for this Windows user. It is excluded from Git.'
}
$pointer = [IntPtr]::Zero
try {
    if (Test-Path -LiteralPath $secretPath) {
        $mailSecret = Import-Clixml -LiteralPath $secretPath
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($mailSecret)
        $env:PUBLIC_FUND_MAIL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer).Replace(' ', '')
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $pointer = [IntPtr]::Zero
        $mailSecret.Dispose()
    } else {
        Write-Warning 'Email is not configured yet. Run .\Start-Backend.ps1 -ConfigureMail once.'
    }
    if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-22') { $env:JAVA_HOME='C:\Program Files\Java\jdk-22' }
    Push-Location $PSScriptRoot
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'Backend stopped. Read the startup error above.' }
    } finally { Pop-Location }
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    Remove-Item Env:PUBLIC_FUND_MAIL_PASSWORD -ErrorAction SilentlyContinue
}

