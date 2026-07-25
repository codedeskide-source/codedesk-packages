CODE DESK JAVA LANGUAGE SUPPORT
Version 1.0.0

OVERVIEW

This package provides official Java language support for Code Desk.

The package defines:

- Java source-file recognition
- Java keywords and literals
- Common Java types
- Code-completion snippets
- Java source templates
- JDK discovery rules
- javac compiler commands
- Java compiler error parsing
- Java 17 as the minimum supported Java version


SYSTEM REQUIREMENTS

A Java Development Kit must be installed to compile Java projects.

The minimum supported version is:

Java Development Kit 17

A Java Runtime Environment alone is not sufficient because it may not
contain the javac compiler.


JDK DETECTION

Code Desk may locate the Java compiler using:

1. The system PATH
2. The JAVA_HOME environment variable
3. The JDK_HOME environment variable
4. A manually selected JDK directory
5. A project-specific compiler configuration


SUPPORTED FILES

Primary source extension:

.java

Special recognized filenames:

module-info.java
package-info.java


COMPILATION

Compiled class files should be placed in the project's configured output
directory.

The default output directory may be:

build/classes

Code Desk should preserve the project's package structure when compiling
source files.


EXTERNAL LIBRARIES

External JAR libraries may be added to a project's classpath.

Windows classpaths use a semicolon separator.

Linux and macOS classpaths use a colon separator.


TEMPLATES

This package includes templates for:

- Standard class
- Interface
- Enumeration
- Main application class

Template placeholders are replaced by Code Desk when a new file is created.


PACKAGE OWNERSHIP

Publisher:

Code Desk

Repository:

codedeskide-source/codedesk-packages

This package is intended for use with the Code Desk integrated development
environment.
