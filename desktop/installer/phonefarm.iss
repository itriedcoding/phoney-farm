; Phone Farm Desktop installer.
; Built in CI with: ISCC.exe /DMyAppVersion=1.2.1 desktop\installer\phonefarm.iss
; The app image itself comes from: gradlew -p desktop createDistributable

#ifndef MyAppVersion
  #define MyAppVersion "1.2.2"
#endif

#define MyAppName "Phone Farm Desktop"
#define MyAppExeName "PhoneFarmDesktop.exe"
#define MyAppPublisher "Phone Farm"

[Setup]
AppId={{8F2D5F0E-2F8D-4B8F-9F1D-2C7F9C5A1B30}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\PhoneFarmDesktop
DefaultGroupName=Phone Farm
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\..\dist\windows
OutputBaseFilename=PhoneFarmDesktop-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
MinVersion=10.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "..\build\compose\binaries\main\app\PhoneFarmDesktop\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
