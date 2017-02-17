TracInstant - A tool to search, sort and preview Trac (http://trac.edgewall.org) tickets in real-time.

## Quick start

To build and run from source, simply execute: `gradlew run`

 * The only prerequisites is having Java >= 8 installed.
 * The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) automatically downloads Gradle (if necessary), the project dependencies, builds the .jar and launches it with default settings.

## Usage

Basic usage should be self-explanatory: fire it up, enter the URL to a Trac server, start searching.

Text searches include all visible fields and the main description (but not comments). See the tooltip of the search field for tips to refine your queries.

Data is cached locally so that subsequent runs launch quickly, and to avoid overloading Trac servers. It then updates incrementally (each time the window gets focus). It operates 100% via the web interface.

## Other ways to build:

To build an installation, execute: `gradlew installDist`. To launch it:

   1. Verify `$JAVA_HOME` is set in your environment
   2. Execute `build/install/TracInstant/bin/TracInstant(.bat)`
   3. You can e.g. create a Windows shortcut to this

Alternatively you can import it into your favourite IDE to build and run.
 * For Eclipse, import the "existing Java Project". (Buildship shouldn't be necessary).
 * See file `build.gradle` for some recommended JVM settings to put in the launch configuration.

## Contribute

Feature suggestions & pull-requests are welcome. A collection of ideas are listed in the file `TODO`. Feel free to raise an 'issue' (even for things listed in the `TODO`) so that I know there's interest for it.