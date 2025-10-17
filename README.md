# Cacummaro - Web PDF Ingest & Categorization Application

**Cacummaro - From Latin "cacumen" (peak, summit) - reaching the peak of document organization!**

**A comprehensive Java application for ingesting web pages, converting them to PDF, extracting metadata, classifying documents, and integrating with Obsidian for knowledge management.**

##  Features

- **Web to PDF**: Convert any web page to PDF using Playwright headless Chrome
- **AI-Powered Classification**: Machine learning + MCP integration with GPT-4, Claude, or custom AI models
- **Hybrid Classification System**: Combines TF-IDF ML, rule-based patterns, and external AI for maximum accuracy
- **MCP Protocol Support**: Integrate with any AI model via Model Context Protocol (future-proof architecture)
- **Interactive Graph Visualization**: Obsidian-style category graph with D3.js
- **CouchDB Storage**: Robust document storage with attachment support
- **Obsidian Integration**: Automatic note generation with frontmatter and metadata
- **Full-text Search**: Search across titles, descriptions, and metadata
- **REST API**: Comprehensive API for all operations
- **Security**: SSRF protection, input validation, and path traversal prevention

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web UI        │    │   REST API      │    │   Services      │
│                 │◄──►│                 │◄──►│                 │
│ - Graph View    │    │ - /ingest       │    │ - PDF Gen       │
│ - Category List │    │ - /documents    │    │ - AI Classifier │
│ - Document View │    │ - /categories   │    │ - Obsidian      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │                        │
                                ▼                        ▼
┌──────────────────────────────────────────┐  ┌─────────────────┐
│        Classification Pipeline            │  │  Obsidian Vault │
│  ┌────────────────────────────────────┐  │  │                 │
│  │ 1. MCP AI Models (Optional)        │  │  │ - Notes (.md)   │
│  │    • GPT-4, Claude, Custom LLMs    │  │  │ - Links         │
│  │    • JSON-RPC 2.0 Protocol         │──┼──┤ - Metadata      │
│  └────────────────────────────────────┘  │  └─────────────────┘
│  ┌────────────────────────────────────┐  │
│  │ 2. ML TF-IDF Classifier            │  │  ┌─────────────────┐
│  │    • PDF Text Extraction           │  │  │    CouchDB      │
│  │    • Cosine Similarity             │  │  │                 │
│  └────────────────────────────────────┘  │  │ - Documents     │
│  ┌────────────────────────────────────┐  │  │ - Categories    │
│  │ 3. Rule-Based Patterns             │  │  │ - PDF Binaries  │
│  │    • Keyword Matching              │──┼──┤ - ML Models     │
│  │    • Domain Detection              │  │  └─────────────────┘
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │ 4. Merge & Deduplicate Results     │  │
│  │    • Highest Confidence Wins       │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

- **Java 11+** (LTS recommended)
- **Docker** (for CouchDB)
- **Maven 3.6+**
- **curl** (for testing)

### 1. Clone and Start

```bash
# Linux/macOS
./start.sh

# Windows
start.bat
```

### 2. Manual Setup (Alternative)

```bash
# Start CouchDB
docker-compose up -d

# Build application
mvn clean package

# Run application
java -jar target/cacummaro-1.0-SNAPSHOT.jar
```

### 3. Verify Installation

```bash
# Health check
curl http://localhost:8082/actuator/health

# Expected response:
# {"status":"UP"}

# Access the web interface
# Open browser to http://localhost:8082/
```

## API Usage

### Web Interface

Open your browser to `http://localhost:8082/`:

1. **Ingest Documents**: Enter any URL and click "Convert" to create a PDF snapshot
2. **View Graph**: Click "Smart Classification" to see the interactive category graph
3. **Download PDFs**: Click on any category node, then download PDFs from the sidebar

### API Examples

#### Ingest a Web Page

```bash
curl -X POST http://localhost:8082/api/v1/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/article",
    "options": {
      "createObsidianNote": true,
      "noteMetaTag": "data-note"
    }
  }'
```

Response:
```json
{
  "id": "doc|550e8400-e29b-41d4-a716-446655440000",
  "status": "STORED",
  "pdfUrl": "/api/v1/documents/doc|550e8400-e29b-41d4-a716-446655440000/pdf"
}
```

#### Search Documents

```bash
curl "http://localhost:8082/api/v1/search?q=machine%20learning&size=5"
```

#### Download PDF

```bash
curl -o document.pdf \
  "http://localhost:8082/api/v1/documents/doc|550e8400-e29b-41d4-a716-446655440000/pdf"
```

#### List Categories

```bash
curl "http://localhost:8082/api/v1/categories"
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
cacummaro:
  couchdb:
    host: localhost
    port: 5984
    database: cacummaro_docs
    username: admin
    password: password

  obsidian:
    vault-path: ./obsidian-vault
    enabled: true

  pdf:
    timeout: 30s
    max-page-size: 10MB

  # Classification System Configuration
  classification:
    confidence-threshold: 0.7

    # Machine Learning TF-IDF Classifier
    ml:
      enabled: true
      confidence-threshold: 0.6
      model-path: ./ml-model.json
      min-document-frequency: 2
      max-features: 1000

    # MCP AI Integration (GPT-4, Claude, Custom LLMs)
    mcp:
      enabled: false  # Set to true to enable AI classification
      server-url: http://localhost:3000/mcp
      timeout-ms: 30000
      tool-name: classify_document

  security:
    allowed-domains: []
    blocked-internal-ips: true

  ingestion:
    max-concurrent: 5
    retry-attempts: 3
    retry-delay: 5s
```

## 📁 Project Structure

```
src/main/java/org/cacummaro/
├── domain/              # Core entities (Document, Category)
├── dto/                 # API contracts (IngestRequest, etc.)
├── repository/          # Data access (CouchDB implementations)
├── service/
│   ├── pdf/            # PDF generation (Playwright)
│   ├── classification/ # Document classification
│   └── obsidian/       # Note generation
├── controller/         # REST endpoints
└── config/             # Spring configuration
```

## Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn failsafe:integration-test

# All tests
mvn verify
```

##  Security Features

- **SSRF Protection**: Blocks internal/private IP ranges
- **Input Validation**: Comprehensive URL and data validation
- **Path Traversal Prevention**: Secure Obsidian vault operations
- **Sanitization**: Clean filenames and content
- **Rate Limiting**: Configurable request limits

## AI-Powered Classification System

Cacummaro features a **next-generation hybrid classification system** that combines the best of traditional ML, modern AI, and rule-based approaches.

### Three-Layer Classification Architecture

#### 1.  MCP AI Models (External Intelligence)
**The Future of Document Classification**

Integrates with cutting-edge AI models via the **Model Context Protocol (MCP)**:

- **GPT-4 / GPT-4 Turbo**: Deep semantic understanding and contextual reasoning
- **Claude (Anthropic)**: Nuanced categorization with reasoning explanations
- **Local LLMs**: Ollama, LLaMA, Mistral for privacy-focused deployments
- **Custom Models**: Your fine-tuned domain-specific AI models

**Benefits:**
-  **Semantic Understanding**: Goes beyond keywords to understand context and meaning
-  **Continuous Learning**: Leverage latest AI advancements without code changes
-  **Multi-language**: Works across languages with advanced AI models
-  **Confidence Scores**: AI provides reasoning behind classifications
-  **Plug & Play**: Swap AI models by changing MCP server configuration

**Configuration:**
```yaml
cacummaro:
  mcp:
    enabled: true
    server-url: http://localhost:3000/mcp  # Your MCP server
    timeout-ms: 30000
    tool-name: classify_document
```

**Example MCP Response:**
```json
{
  "categories": [
    {
      "name": "artificial-intelligence",
      "confidence": 0.95,
      "reasoning": "Article discusses neural networks, deep learning, and ML algorithms"
    }
  ],
  "model": "gpt-4"
}
```

#### 2.  ML TF-IDF Classifier (Local Intelligence-->wikipedia source "tf–idf")

**Content-Based Machine Learning**

- Extracts full text from PDF documents using Apache PDFBox
- Uses **TF-IDF vectorization** (Term Frequency-Inverse Document Frequency)
- **Cosine similarity** for category matching
- Learns from your document corpus
- **Always available** as fallback when MCP is unavailable

**Training the Model:**
```bash
# Train on existing categorized documents
curl -X POST http://localhost:8082/api/v1/ml/train?maxDocuments=1000

# Check ML status
curl http://localhost:8082/api/v1/ml/status
```

**Benefits:**
-  **Fast**: Local execution, no API calls
-  **Private**: No data leaves your infrastructure
-  **Self-Learning**: Improves as you categorize more documents
-  **Reliable**: Always available, no external dependencies

#### 3.  Rule-Based Classifier (Pattern Matching)

**Traditional keyword and domain-based classification**

Built-in categories with pattern matching:
- **Technology**: `software, programming, ai, javascript, python, react, docker`
- **Finance**: `banking, investment, crypto, trading, stocks, portfolio`
- **Science**: `research, biology, physics, medical, study, experiment`
- **Business**: `startup, management, strategy, marketing, sales, leadership`
- **News**: `breaking, politics, government, journalism, election`

Add custom categories in `application.yml`:
```yaml
cacummaro:
  classification:
    rule-based:
      category-keywords:
        data-science:
          - "pandas"
          - "numpy"
          - "data analysis"
          - "visualization"
```

### Intelligent Classification Merge

All three classifiers work together:

1. **MCP AI runs first** (if enabled) for deep semantic understanding
2. **ML TF-IDF analyzes** PDF content for statistical patterns
3. **Rule-based checks** for explicit keywords and domains
4. **Results are merged** - highest confidence wins per category
5. **Source tracking** - know which classifier found each category

**Example Merged Result:**
```json
{
  "categories": [
    {
      "name": "artificial-intelligence",
      "confidence": 0.95,
      "classifier": "mcp-gpt4"  ← AI model
    },
    {
      "name": "technology",
      "confidence": 0.88,
      "classifier": "ml-tfidf-v1.0"  ← Local ML
    },
    {
      "name": "programming",
      "confidence": 0.75,
      "classifier": "enhanced-rule-based-v1.0"  ← Rules
    }
  ]
}
```

##  Obsidian Integration

Generated notes include:

```markdown
---
source_url: https://example.com
title: Article Title
pdf_id: doc|uuid
fetchedAt: 2025-01-15T10:30:00Z
tags: [technology, page-snapshot]
---

# Article Title

## Description
Article description from meta tags

## Notes
Content from configurable meta tag

## Resources
[Download PDF](../pdfs/doc|uuid.pdf)

## Categories
- technology (confidence: 0.85)

## Metadata
- **Fetched:** 2025-01-15T10:30:00Z
- **PDF Size:** 1.2 MB
```

##  Troubleshooting

### Common Issues

1. **CouchDB Connection Failed**
   ```bash
   docker-compose logs couchdb
   curl http://localhost:5984/
   ```

2. **PDF Generation Timeout**
   ```yaml
   cacummaro:
     pdf:
       timeout: 60s  # Increase timeout
   ```

3. **Java Version Issues**
   ```bash
   java -version  # Must be 11+
   ```

4. **Permission Issues (Linux/macOS)**
   ```bash
   chmod +x start.sh
   ```

### Logs

Application logs are available in the console output. Key components:

- `org.cacummaro.service.pdf`: PDF generation
- `org.cacummaro.service.classification`: Document classification
- `org.cacummaro.repository`: Database operations

## 🤝 Development

### Adding New Classifiers

1. Implement the `Classifier` interface:
   ```java
   @Service
   public class MyCustomClassifier implements Classifier {
       // Implementation
   }
   ```

2. Register as Spring Bean
3. Configure in `application.yml`

### Adding New PDF Generators

1. Implement the `PdfGenerator` interface
2. Replace or add as alternative implementation

##  Monitoring

- **Web Interface**: `http://localhost:8082/`
- **Graph Visualization**: `http://localhost:8082/graph`
- **Health Endpoint**: `http://localhost:8082/actuator/health`
- **CouchDB Admin**: `http://localhost:5984/_utils/`
- **Application Metrics**: Available via Spring Actuator


### Tomcat Deployment

```bash
# Build WAR file
mvn clean package -Pwar

# Deploy to Tomcat
cp target/cacummaro-1.0-SNAPSHOT.war $TOMCAT_HOME/webapps/
```

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

## 🚧 Roadmap

### ✅ Completed
- [x] Web Interface with Thymeleaf
- [x] Interactive Graph Visualization (D3.js)
- [x] ML-based Classification (TF-IDF with PDF text extraction)
- [x] MCP Integration (AI models via Model Context Protocol)
- [x] Hybrid Classification (ML + AI + Rules)

### 🚀 In Progress
- [ ] Enhanced MCP Features
  - [ ] Multi-model voting (query multiple AI models, consensus-based results)
  - [ ] Streaming classification results
  - [ ] Fine-tuning integration APIs
- [ ] Classification Analytics Dashboard
  - [ ] Accuracy metrics per classifier
  - [ ] Confidence distribution charts
  - [ ] Category suggestion improvements

### 📋 Planned
- [ ] Batch Processing Queue for large-scale ingestion
- [ ] Docker Image with embedded ML models
- [ ] Kubernetes Deployment manifests
- [ ] Advanced Search with Elasticsearch integration
- [ ] Multi-user Support with role-based access
- [ ] API Authentication (OAuth2, JWT)
- [ ] Active Learning: User feedback improves classification
- [ ] Category recommendations based on document content

---



##  Debugging (Windows)

To run the application in debug mode (waiting for a debugger to attach on port 5005), use the following command in **cmd.exe**:

```cmd
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
```

- The application will wait for a debugger to connect before starting.
- You can then attach your IDE (e.g., IntelliJ, Eclipse) to `localhost:5005`.
- Make sure to use double quotes as shown above when running in Windows cmd.exe.
