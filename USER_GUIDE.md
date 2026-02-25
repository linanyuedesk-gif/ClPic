# Car Pic Viewer - User Guide

## Quick Start

### Bottom Control Bar
Located at the bottom of the screen with 4 main buttons:

1. **⟨ PREV** - Previous Image
   - Navigate to the previous image in current playlist

2. **☀ MODE** - Toggle Brightness Mode
   - Switch between Bright Mode (Mode 1) and Dark Mode (Mode 2)
   - Animated brightness transition for smooth visual change

3. **NEXT ⟩** - Next Image
   - Navigate to the next image in current playlist

4. **⚙ CONFIG** - Configuration Panel
   - Open settings and image management panel
   - Long-press to hide/show the navigation bar

---

## Gesture Controls

### Single Tap
- **On Image**: Toggle between Brightness modes

### Double Tap
- **On Image**: Open Configuration panel

### Horizontal Swipe
- **Left**: Next image
- **Right**: Previous image

### Two-Finger Pinch
- **Outward**: Zoom in on image
- **Inward**: Zoom out on image

### Two-Finger Drag
- **Any direction**: Pan floated image around screen

### Two-Finger Triple Tap (Advanced)
- **Switch between Public/Private mode**: Separate image history for private content

---

## Brightness Control

### Mode 1 (Bright Mode - Default)
- **Activate**: Tap once on image or press MODE button
- **Adjust**: Slide finger horizontally left/right to adjust brightness
- **Range**: 0% (minimum) to 100% (maximum)

### Mode 2 (Dark Mode)
- **Activate**: Tap once on image or press MODE button
- **Adjust**: Slide finger vertically up/down to adjust brightness
- **Range**: 0% (screen off) to 100% (very bright)

### Brightness Indicator
- Appears during brightness adjustment
- Shows current mode and percentage
- Auto-hides after 2 seconds of no adjustment

---

## Configuration Panel

Access via:
- Double-tap the image
- Press the **⚙ CONFIG** button

### Features:

#### Select Local Image
- Browse and select image files from your device
- Supports: JPG, PNG, BMP, WEBP, GIF
- Saves selected image to history

#### Scan Device
- Automatically scans device storage for images
- Searches: DCIM, Pictures, Downloads, ClPic folder
- Creates a playlist of found images
- Shows total count when complete

#### Load from URL
- Enter image URL (must be HTTPS)
- Supports common image formats
- Click **Load** to fetted image
- Image is cached for faster future loading

#### Recent Images
- Scrollable list of previously viewed images
- Click any item to load that image again
- Local images show filename
- URLs show their address

---

## Tips & Tricks

### For Car Safety
- Keep both hands on wheel when possible
- Use gestures when parked or at stops
- Use larger MODE button for brightness adjustment while driving

### For Better Performance
- Use **Brightness Mode 1** in daytime (preserves battery)
- Use **Brightness Mode 2** at night (less eye strain)
- Adjust to comfortable level for current lighting conditions

### For Image Management
- Regularly cleared unused images from recent list
- Use **Scan Device** to quickly load all images
- Create a **ClPic** folder in external storage for easy access

### Pro Tips
- **Persistent State**: App remembers last image, brightness levels, and zoom position
- **Automatic Cleanup**: Private mode history is separate from public
- **Quick Navigation**: Swipe left/right while zoomed in to navigate without unzooming
- **Zoom Lock**: After zooming, pan with two fingers to explore zoomed view

---

## Troubleshooting

### Image Won't Load
- Check image URL is correct (must start with https://)
- Ensure device has internet connection
- Check storage permissions are granted
- Try loading a different image to verify app works

### No Images Found on Scan
- Ensure storage permission is granted
- Check if images are in supported folders (DCIM, Pictures, Downloads)
- Move images to standard folders
- Create a **ClPic** folder in external storage with images

### Brightness Slider Not Appearing
- Make sure you're swiping, not tapping
- Swipe must be at least 24 pixels (prevent accidental adjustment)
- Check brightness indicator visibility in app settings

### App Crashes or Freezes
- Restart the app
- Check available device storage
- Clear app cache in device settings
- Restart device if persistent

---

## Permissions Required

The app requests these permissions to function:

- **Read External Storage** (Android 12 and below)
- **Read Media Images** (Android 13+)
- **Internet** (for loading images from URLs)
- **Wake Lock** (to keep screen on during car use)
- **Receive Boot Completed** (to auto-start the app)

All permissions can be managed in device settings under **Apps > Car Pic Viewer**.

---

## Keyboard Shortcuts (Car Head Unit)

If your car head unit supports physical buttons:

- **Volume Up**: Next image
- **Volume Down**: Previous image
- **OK/Select**: Toggle brightness mode
- **Back/Home**: Hide/Show controls

Note: This depends on your car's infotainment integration.

---

## Data Storage

The app stores locally:

- Image history (public and private)
- Brightness preferences per image
- Last viewed image
- Zoom position and pan offset

All data is stored in app private storage. Uninstalling the app clears all saved data.

---

## Version Info

**Car Pic Viewer v1.0**
- Optimized for landscape orientation
- Fullscreen display mode
- Car infotainment system optimized

---

## Contact & Support

For bugs, feature requests, or questions, please refer to the GitHub repository or contact the development team.

---

**Safety First**: Never operate brightness or zoom controls while actively driving. Set preferences while parked or at stops only.
