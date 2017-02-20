# TracInstant Change Log

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [Unreleased]

## 0.1.0 - 2017-02-20
### Added
 - Support for Trac v1.0 - v1.2. (v0.12 might still work but is mostly untested.)
 - Offer to save user password, for better startup + user experience

### Changed
 - Requries Java 8 (previously Java 6)
 - Improve re-connection and avoid prompting (e.g. on network failure)
 - Search (and create) sub-directories when downloading external file attachments

### Fixed
 - A small memory improvement
 - Avoid a UI lag when Window gains focus (before incrementally updating)

### Notes

 Functionality broken by Trac 1.2 generally revolved around new display names
 for fields. e.g. Where a query (including those used by Trac itself) might request a
 field using an ID (like `changetime`) Trac v1.2 might now reply using a different
 label for the field (like "Modified").
