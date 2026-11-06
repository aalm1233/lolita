#!/usr/bin/env python3
"""Capture complete native windows (including dialogs/IME) on an explicit test AVD."""
import argparse
from pathlib import Path
import subprocess
import time
import re

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--serial',required=True)
    p.add_argument('--url',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--methods',default='fourteenSkinsCachedOfflineFeedAndServiceDialog,serviceAddressValidationCancelAndRealKeyboard,disconnectedEmptyStateRefreshIsSilentAndImportStaysAvailable')
    args=p.parse_args();assert re.fullmatch(r'emulator-[0-9]+',args.serial)
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
    adb=[str(Path.home()/'Library/Android/sdk/platform-tools/adb'),'-s',args.serial]
    marker='files/square-network-capture.txt'
    subprocess.run(adb+['shell','run-as','com.lolita.app','rm','-f',marker],check=True)
    classes=','.join('com.lolita.app.ui.screen.square.SquareNetworkTest#'+m for m in args.methods.split(','))
    with (out/'instrumentation.log').open('wb') as log:
        proc=subprocess.Popen(adb+['shell','am','instrument','-w','-r','-e','squareServiceUrl',args.url,'-e','externalScreenshots','true','-e','class',classes,'com.lolita.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT)
        deadline=time.monotonic()+240
        while proc.poll() is None:
            if time.monotonic()>deadline:
                proc.terminate();raise RuntimeError('Native validation timed out')
            result=subprocess.run(adb+['shell','run-as','com.lolita.app','cat',marker],capture_output=True,text=True,timeout=5)
            name=result.stdout.strip()
            if result.returncode==0 and re.fullmatch(r'[a-z0-9-]+',name):
                time.sleep(.15)
                content=subprocess.check_output(adb+['exec-out','screencap','-p'],timeout=10)
                assert content.startswith(b'\x89PNG\r\n\x1a\n')
                (out/(name+'.png')).write_bytes(content)
                subprocess.run(adb+['shell','run-as','com.lolita.app','rm',marker],check=True)
            else:time.sleep(.1)
        proc.wait()
    result=(out/'instrumentation.log').read_text()
    assert re.search(r'OK \([0-9]+ tests?\)',result),result[-5000:]
    print('Native captures:',len(list(out.glob('*.png'))))

if __name__=='__main__':main()
