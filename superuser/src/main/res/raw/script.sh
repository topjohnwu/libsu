#!/system/bin/sh

ui_print() {
  echo "$1"
}

resolve_link() {
  RESOLVED="$1"
  while RESOLVE=`readlink $RESOLVED`; do
    RESOLVED=$RESOLVE
  done
  echo $RESOLVED
}

is_mounted() {
  TARGET="`resolve_link $1`"
  cat /proc/mounts | grep " $TARGET " >/dev/null
  return $?
}

find_boot_image() {
  BOOTIMAGE=
  if [ ! -z $SLOT ]; then
    BOOTIMAGE=`find /dev/block -iname boot$SLOT | head -n 1` 2>/dev/null
  fi
  if [ -z "$BOOTIMAGE" ]; then
    # The slot info is incorrect...
    SLOT=
    for BLOCK in boot_a kern-a android_boot kernel boot lnx bootimg; do
      BOOTIMAGE=`find /dev/block -iname $BLOCK | head -n 1` 2>/dev/null
      [ ! -z $BOOTIMAGE ] && break
    done
  fi
  # Recovery fallback
  if [ -z "$BOOTIMAGE" ]; then
    for FSTAB in /etc/*fstab*; do
      BOOTIMAGE=`grep -v '#' $FSTAB | grep -E '/boot[^a-zA-Z]' | grep -oE '/dev/[a-zA-Z0-9_./-]*'`
      [ ! -z $BOOTIMAGE ] && break
    done
  fi
  [ ! -z "$BOOTIMAGE" ] && BOOTIMAGE=`resolve_link $BOOTIMAGE`
}

# Check A/B slot
SLOT=`getprop ro.boot.slot_suffix`
if [ -z $SLOT ]; then
  SLOT=_`getprop ro.boot.slot`
  [ $SLOT = "_" ] && SLOT=
fi

# Check the boot image to make sure the slot actually make sense
find_boot_image
ui_print "- Found boot image: $BOOTIMAGE"
[ -z $SLOT ] || ui_print "- A/B partition detected, current slot: $SLOT"

cat /proc/mounts | grep -E '/dev/root|/system_root' >/dev/null && SKIP_INITRAMFS=true || SKIP_INITRAMFS=false
if [ -f /system/init.rc ]; then
SKIP_INITRAMFS=true
fi
$SKIP_INITRAMFS && ui_print "- Device skip_initramfs detected"
