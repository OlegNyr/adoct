---
apply: always
mode: all
---

# PRD: JetBrains IDE Plugin for AsciiDoc

## 1. Introduction

### 1.1 Purpose
A plugin for JetBrains IDE that provides tools for working with AsciiDoc files, including importing documents from Confluence and other sources, format conversion, and displaying content in a user-friendly way.

### 1.2 Target Audience
- Developers using JetBrains IDE
- Technical writers and documenters
- Technical documentation specialists working with Confluence

## 2. Functionality Overview

### 2.1 Key Components

#### 2.1.1 Tool Window
- **FileImporterToolWindow**: Tool panel for importing files and documents
- **FileImporterToolWindowFactory**: Factory for creating the tool window
- **ShowFileImporterAction**: Action to display the file import window

#### 2.1.2 Document Conversion
- **ConvertStorageToAdoc**: Main class for converting documents from Confluence to AsciiDoc
- **ConvertDocsToAdocZip**: Conversion of documents to ZIP archives in AsciiDoc format

#### 2.1.3 Parsers
- **ParseDispatcher**: Document parsing dispatcher
- **ParseHeader**, **ParseParagraph**, **ParseTable**, **ParseLink**, **ParseList**, **ParseTime**, **ParseImg**, **ParseImgAcc**, **ParsePlaceholder**, **ParseCommentId**, **ParseTagMacros**: Parsers for various document elements
- **ParseMacrosDispatcher**: Macros dispatcher
- **MacrosAnchor**, **MacrosCode**, **MacrosDrawio**, **MacrosExpand**, **MacrosJira**, **MacrosNote**, **MacrosNumber**, **MacrosPlantuml**, **MacrosStep**: Macro implementations

#### 2.1.4 Confluence Client
- **ConfluenceClient**: Client for interacting with Confluence API
- **ConfluenceSsl**: SSL settings for Confluence
- **ContentPage**, **ContextPage**: Data structures for pages
- **LinkResult**: Link search result
- **UserDto**: User data

#### 2.1.5 Color Processing
- **ColorMatcher**, **ColorParser**: Color matching and parsing mechanisms
- **colors.json**: Color database for conversion

## 3. Technical Details

### 3.1 Dependencies
- IntelliJ Platform Plugin SDK
- Lombok
- Apache Commons IO
- Jsoup (HTML parser)
- Jackson (JSON processing)
- Confluence REST API

### 3.2 Project Structure
```
AsciiDocTools/
├── src/main/java/org/tools/asciidoc/plugins/idea/
│   ├── action/ShowFileImporterAction.java
│   ├── ui/FileImporterToolWindow.java
│   ├── ui/FileImporterToolWindowFactory.java
│   └── Bundle.java
├── src/main/java/ru/sbtf/ub/brok/arh/
│   ├── ConvertDocsToAdocZip.java
│   ├── ConvertStorageToAdoc.java
│   ├── DispatcherPage.java
│   ├── EndpointFile.java
│   ├── HeaderText.java
│   ├── LinksAtachment.java
│   ├── LinksAttachment.java
│   ├── LinksPage.java
│   ├── LinksUser.java
│   ├── LinksValue.java
│   ├── client/ (Confluence clients)
│   ├── color/ (color processing)
│   ├── parser/ (parsers)
│   └── ...
└── src/main/resources/
    ├── META-INF/plugin.xml
    ├── messages/Bundle.properties
    └── colors.json
```

### 3.3 Key Classes

#### 3.3.1 ConvertStorageToAdoc
- **Purpose**: Conversion of Confluence HTML content to AsciiDoc
- **Main Methods**:
  - `convert()`: Main conversion method
  - `resolveLink()`: Link resolution
  - `getLinks()`: Retrieve links from document
  - `getColors()`: Retrieve used colors

#### 3.3.2 ParseDispatcher
- **Purpose**: HTML element processing dispatcher
- **Main Methods**:
  - `parse()`: Element processing
  - `parseText()`: Text processing

#### 3.3.3 ConfluenceClient
- **Purpose**: Interaction with Confluence API
- **Main Methods**:
  - `getMainPage()`: Get main page
  - `search()`: Search pages
  - `getAttachments()`: Get attachments
  - `downLoad()`: Download attachments

## 4. Functional Requirements

### 4.1 Document Import
- Import pages from Confluence to AsciiDoc format
- Attachment support (images, files)
- Preserve document structure

### 4.2 Format Conversion
- Conversion of Confluence HTML to AsciiDoc
- Support for Confluence macros (expand, code, plantuml, drawio, etc.)
- Processing of tables, lists, headers
- Preserve colors and styles

### 4.3 Link Handling
- Resolution of internal Confluence links
- Processing of user, page, and attachment links
- Generation of correct links in AsciiDoc

### 4.4 UI Integration
- Tool Window for function access
- Integration with IDE context menu
- Display operation progress

## 5. Non-Functional Requirements

### 5.1 Performance
- Optimization for large document processing
- Asynchronous processing to prevent UI blocking

### 5.2 Compatibility
- Support for latest JetBrains IDE versions
- Compatibility with various Confluence versions

### 5.3 Extensibility
- Modular architecture for adding new parsers and macros
- Configurable conversion rules

## 6. Planned Improvements

### 6.1 Short-Term
- Improved macro handling
- Expanded format support
- Performance optimization

### 6.2 Long-Term
- Support for other content management systems
- Integration with version control systems
- Support for collaborative editing

## 7. Current Project Status

The project is under active development. The main functionality for converting documents from Confluence to AsciiDoc is implemented and operational. Current efforts are focused on integration with JetBrains IDE through the plugin.

### 7.1 Implemented Features
- Conversion of HTML from Confluence to AsciiDoc
- Support for main macros
- Attachment handling
- Color and style processing
- Basic integration with JetBrains IDE

### 7.2 In Development
- UI/UX improvement
- Expanded macro support
- Performance optimization
