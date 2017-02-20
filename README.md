**TracInstant** - _A desktop app to search, sort and preview [Trac](http://trac.edgewall.org) tickets in real-time._

## Quick start

Requires Java 8. To build and run from source, simply execute:

    gradlew run

> Tip: Installing Gradle is not necessary, as the included [gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) will automatically download project dependencies, build the .jar and launch the app with default settings.

## Usage

Basic usage should be self-explanatory: fire it up, enter the URL to a Trac server, start searching.

Text searches include all visible fields and the main description (but not comments). See the tooltip of the search field for tips to refine your queries.

Data is cached locally so that subsequent runs launch quickly, and to avoid overloading Trac servers. It then updates incrementally (each time the window gets focus). It operates 100% via the web interface.

## Other ways to build:

To build an installation, execute: `gradlew installDist`. To launch it:

   1. Verify `$JAVA_HOME` is set in your environment
   2. Execute `build/install/TracInstant/bin/TracInstant(.bat)`
   3. You can e.g. create a Windows shortcut to this

Alternatively you can import it into your favourite IDE to build and run. For Eclipse:
 * Buildship is required, to automatically download project dependencies. By going to [Help > Install New Software] you should find it within the standard "Annual Release" update site.
 * Import the "existing Java Project".
 * See file `build.gradle` for some recommended JVM settings to put in the launch configuration.

## Contribute

Feature suggestions & pull-requests are welcome. A collection of ideas are listed in the file `TODO`. Feel free to raise an 'issue' (even for things listed in the `TODO`) so that I know there's interest for it.
