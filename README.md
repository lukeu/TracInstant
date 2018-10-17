**TracInstant** - _A desktop app to search (as you type) and browse [Trac](http://trac.edgewall.org) tickets._

At one point I needed to do a lot of "data mining" and needed ways to filter on specific keywords at the same time as searching the description for free text. And thus, this tool was born.

Features:
 * Search using regular expressions. Add search-terms for specific fields only.
 * Right-click headings to get a summary of all values used, and to filter upon them
 * Find all the ticket numbers in text (for example, pasted from list of email headings)
 * View a histogram overview that updates as you search

Data is cached locally so that subsequent runs launch quickly, and to avoid overloading Trac servers. It then updates incrementally (each time the window gets focus).

## Quick start

To build and run from source (requires JDK >= 8):

    gradlew run

> Tip: Installing Gradle is not necessary; the included [gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) will automatically download any dependencies.

## Usage

Basic usage should be self-explanatory: fire it up, enter the URL to a Trac server, start searching.

Searches include all visible fields and the description (however **not** comments). See the tooltip of the search field for tips.

Click the "Connect..." button (bottom right) to connect to a different server or URL. Look beside this for the connection status, if there is any problem.

## Other ways to build:

### To build an all-in-one Windows executable:

    gradlew createAllExecutables

You'll find the `.exe` under `build/launch4j`, which can be moved anywhere.

### For any operating system, build a "fat jar" using:

    gradlew build

To launch this:

   1. Verify `$JAVA_HOME` is set in your environment
   2. Execute `build/install/TracInstant/bin/TracInstant`

A copy is also zipped and tar'ed to: `build/distributions` to be easily sent to, and extracted by, other users.

### To import and develop using Eclipse
 * Use "Buildship" to import the gradle project. You can find this via the "Eclipse Marketplace" dialog, or go to [Help > Install New Software] where it can be found as part of the standard Simultaneous Release these days.
 * See file `build.gradle` for some recommended JVM settings to put in the launch configuration.

## Contribute

Feature suggestions & pull-requests are welcome! A collection of ideas are listed in the file `TODO`. Feel free to raise any 'issue' so that I know there's interest for it.
