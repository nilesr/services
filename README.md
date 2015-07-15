# core

This project is __*actively maintained*__

It is part of the ODK 2.0 Android tools suite.

It is an APK that provides core services (database, content providers, local webserver) used by all the other ODK 2.0 tools.

The developer [wiki](https://github.com/opendatakit/opendatakit/wiki) (including release notes) and
[issues tracker](https://github.com/opendatakit/opendatakit/issues) are located under
the [**opendatakit**](https://github.com/opendatakit/opendatakit) project.

The Google group for software engineering questions is: [opendatakit-developers@](https://groups.google.com/forum/#!forum/opendatakit-developers)

## Setting up your environment

Install [Android Studio](http://developer.android.com/tools/studio/index.html) and the [SDK](http://developer.android.com/sdk/index.html#Other).

This project depends on the ODK [androidlibrary](https://github.com/opendatakit/androidlibrary) project, so be sure to clone it into the same parent directory as Core.

Now you should be ready to build.

## Building the project

Open the Core project in Android Studio. As long as androidlibrary is in the same parent directory, you should be able to select `Build->Make` Project to build the app.

## Running

If the project builds properly, it should be able to run on an Android device without any other prerequisites.

## Source tree information
Quick description of the content in the root folder:

    |-- core\_app     -- Source tree for Java components

        |-- src

            |-- main

                |-- res     -- Source tree for Android resources

                |-- java

                    |-- org

                        |-- opendatakit

                            |-- core

                                |-- android     -- The most relevant Java code lives here
                                
            |-- androidTest     -- Source tree for Android implementation tests
            |-- test            -- Source tree for Java JUnit tests