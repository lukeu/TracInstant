# TracInstant Change Log

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

## 0.2.1 - 2018-10-17
### Added
 - Undo in the search combo (Ctrl+Z) 
### Fixed
- Support newer Trac versions (tested with v1.2.3).
   - (The default query changed, so Trac needed more "encouragement" to send all ticket data.)
 
## 0.2.0 - 2018-07-25
### Added
 - New "Find tickets in text" result formats to paste into Microsoft Outlook's search field

### Fixed
 - Fix hyperlinks to open in a browser on KDE
 - Try to keep at least one selected table-row in view as the results update

### Changed
 - Make copyright headers more inclusive, and make package names point to github

## 0.1.0 - 2017-02-22
### Added
 - Support for Trac v1.0 - v1.2. (v0.12 might still work but is mostly untested.)
 - Offer to save user password, for better startup + user experience

### Changed
 - Requries Java 8 (previously Java 6)
 - Improve re-connection and avoid prompting (e.g. on network failure)
 - Search (and create) sub-directories when downloading external file attachments
 - Switch to Gradle to automate and self-document the build. (Any step is now a 1-liner!)

### Fixed
 - A small memory improvement
 - Avoid a UI lag when Window gains focus (before incrementally updating)
 - Site annotations could be lost when switching between sites

### Notes

  Functionality broken by Trac 1.2 generally revolved around new display names
  for fields. e.g. Where a query (including those used by Trac itself) might request a
  field using an ID (like `changetime`) Trac v1.2 might now reply using a different
  label for the field (like "Modified").

## 0.0.0 - 2012-11-07
### Initial offering

  Pushed out this experimental project I'd been hacking on in my spare time -
  it had started gaining popularity internally in a company I work for.
