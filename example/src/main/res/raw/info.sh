#!/system/bin/sh

grep_prop() {
  REGEX="s/^$1=//p"
  shift
  FILES=$@
  [ -z "$FILES" ] && FILES='/system/build.prop'
  sed -n "$REGEX" $FILES 2>/dev/null | head -n 1
}

api_level_arch_detect() {
  API=`grep_prop ro.build.version.sdk`
  ABI=`grep_prop ro.product.cpu.abi | cut -c-3`
  ABI2=`grep_prop ro.product.cpu.abi2 | cut -c-3`
  ABILONG=`grep_prop ro.product.cpu.abi`

  ARCH=arm
  IS64BIT=false
  if [ "$ABI" = "x86" ]; then ARCH=x86; fi;
  if [ "$ABI2" = "x86" ]; then ARCH=x86; fi;
  if [ "$ABILONG" = "arm64-v8a" ]; then ARCH=arm64; IS64BIT=true; fi;
  if [ "$ABILONG" = "x86_64" ]; then ARCH=x64; IS64BIT=true; fi;
}

TOOLPATH=`which toolbox`
TOYPATH=`which toybox`
BUSYPATH=`which busybox`

api_level_arch_detect

# Check A/B slot
SLOT=`getprop ro.boot.slot_suffix`
if [ -z $SLOT ]; then
  SLOT=_`getprop ro.boot.slot`
  [ $SLOT = "_" ] && SLOT=
fi

cat /proc/mounts | grep -E '/dev/root|/system_root' >/dev/null && SKIP_INITRAMFS=true || SKIP_INITRAMFS=false

[ ! -z $SLOT ] && echo "Device A/B partition detected, current slot: $SLOT"
$SKIP_INITRAMFS && echo "Device skip_initramfs detected"
echo "Device API: $API"
echo "Device ABI: $ARCH"
[ ! -z $TOOLPATH ] && echo "toolbox at: $TOOLPATH"
[ ! -z $TOYPATH ] && echo "toybox  at: $TOYPATH"
[ ! -z $BUSYPATH ] && echo "busybox at: $BUSYPATH"

