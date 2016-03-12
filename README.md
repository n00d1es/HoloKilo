# HoloKilo
HoloKilo Positional Tracker for Android, created by Kaj Toet

The HoloKilo positional tracker uses (retro)reflective material for markers. To find the markers, the flash next to the camera is activated and the markers will lit up on the camera’s resulting image. However, to exclude lamps and other bright sources of light, the flash is turned off at intervals to find out which lightpoints are lit up by reflectivity and which are not.

##Requirements
- Android 4+ device (possibly before 4; untested)
- Camera with flash
- Gyroscope, accelerometer and magnetometer
- OpenGL ES 2
- A green circular patch of reflective material (blue and red optional), available at local stores

##Media

[![Video 1](http://img.youtube.com/vi/K6ztsdTKuzc/0.jpg)](http://www.youtube.com/watch?v=K6ztsdTKuzc)
[![Video 2](http://img.youtube.com/vi/VQW2xLNd_-Y/0.jpg)](http://www.youtube.com/watch?v=VQW2xLNd_-Y)
[![Video 3](http://img.youtube.com/vi/ydd2h-7mcxk/0.jpg)](http://www.youtube.com/watch?v=ydd2h-7mcxk)

Note: Videos on the right are taken with a Moto G 2nd phone, which is known for its bad gyroscope. The screenrecorder also take away a lot of the smoothness, due to a performance impact. Real life performance is a consistent 30fps on the Moto G 2nd.
Also, videos were taken of an older version than the one on this Github and I've improved latency considerably.

##Possible usecases
- Virtual Reality (VR)
- Augmented Reality (AR)
- Robotics
- Drones
- More..!

##Notes
The Unity implementation seems to have more latency than the Java implementation. This may be due to the fact that I used JavaAndroidObject to communicate with the Java side, which could cause problems due to caching.

There needs to be done some more research on different light conditions. In my testing the exposure lock needs to be on at night and set to minimal exposure in daylight.

Distance calculation happens by the blob size (roughly put). It might be better to use three blobs in a triangle and calculate the distance that way. In an earlier version I also calculated the rotation with Coplanar Posit, but I figured it to be not worth the effort pursueing.

Calculating distance by blob size has some disadvantages due to camera movement causing smear of the blobs. I try to counter this with the gyroscope (more movement is smaller actual blob), but it needs some tweaking.

Currently 3 blobs are tracked (green for camera viewmatrix, red and blue extra for example for controllers), but it is very easy to change this. The blobfinder doesn't need to perform much more work for more blobs.

##Example
https://github.com/Kjos/HoloKilo/raw/master/holokilo-debug.apk

It tracks a circular green (retro) reflective marker. I use a marker of about 10cm in diameter.
