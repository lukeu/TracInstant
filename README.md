TracInstant - A tool to search, sort and preview Trac (http://trac.edgewall.org) tickets in real-time.

##BUILDING 

Do one of the following:
 1. Import the .project into Eclipse, and build and run from there.
 1. Build and run from the command-line using `ant run`
 1. Build using `ant jar`, and launch later using `java -jar TracInstant.jar -Xmx200M`

##USE

Basic usage should be self-explanatory: fire it up, enter the URL to a Trac server, start searching.

Text searches include all visible fields and the main description (but not comments). Data is cached locally so that subsequent runs launch quickly, and to avoid overloading Trac servers. It then updates incrementally (each time the window gets focus). It operates 100% via the web interface.

##CONTRIBUTE

Feature suggestions, patches or pull-requests are all welcome. Take a look at the TODO document to see if there are similar ideas already in mind. For more information, email Luke: ldubox-coding101@yahoo.co.uk
