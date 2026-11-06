#!/usr/bin/env python3
"""Start/stop the local Square API as a macOS user service; no scheduled collector."""
import argparse
from pathlib import Path
import plistlib
import os
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
LABEL='com.lolita.square-api'
PLIST=Path.home()/'Library/LaunchAgents'/f'{LABEL}.plist'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['start','stop','status'])
    p.add_argument('--port',type=int,default=18765)
    args=p.parse_args();domain=f'gui/{os.getuid()}'
    if args.command=='status':
        raise SystemExit(subprocess.call(['launchctl','print',f'{domain}/{LABEL}']))
    if args.command=='stop':
        if PLIST.exists():
            subprocess.run(['launchctl','bootout',domain,str(PLIST)],check=False)
            PLIST.unlink()
        return
    uv=shutil.which('uv')
    if not uv:raise SystemExit('uv is required for the existing local Python environment')
    logs=Path.home()/'Library/Logs/LolitaSquare';logs.mkdir(parents=True,exist_ok=True)
    PLIST.parent.mkdir(parents=True,exist_ok=True)
    if PLIST.exists():subprocess.run(['launchctl','bootout',domain,str(PLIST)],check=False,capture_output=True)
    config=dict(Label=LABEL,ProgramArguments=[uv,'run','--offline','--with','pillow','python',str(ROOT/'scripts/square/lan_service.py'),'serve','--host','0.0.0.0','--port',str(args.port)],
        WorkingDirectory=str(ROOT),RunAtLoad=True,KeepAlive=True,ThrottleInterval=10,
        StandardOutPath=str(logs/'api.log'),StandardErrorPath=str(logs/'api-error.log'),
        EnvironmentVariables=dict(PATH='/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin'))
    PLIST.write_bytes(plistlib.dumps(config));PLIST.chmod(0o600)
    subprocess.run(['launchctl','bootstrap',domain,str(PLIST)],check=True)
    print(f'{LABEL} started on port {args.port}')

if __name__=='__main__':main()
