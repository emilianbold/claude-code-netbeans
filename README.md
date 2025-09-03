# Claude Code NetBeans Plugin

A NetBeans IDE plugin that provides integration with Claude Code through the Model Context Protocol (MCP).

## Features

- **Automatic Detection**: Creates a lock file that Claude Code CLI can discover
- **WebSocket Communication**: Establishes real-time communication using MCP over WebSocket
- **IDE Integration**: Provides access to NetBeans project structure, file operations, and editor content
- **File Operations**: Read, write, and list files through Claude Code
- **Project Management**: Access open projects and project files
- **Document Access**: Retrieve content from open documents in the editor

## Installation

### Prerequisites

- NetBeans IDE 23.0 or later
- Java 11 or later
- Maven 3.6 or later

### Building the Plugin

1. Clone or download this project
2. Navigate to the project directory
3. Build the plugin:

```bash
mvn clean package
```

4. The plugin will be built as `target/claude-code-netbeans-1.0.0.nbm`

### Installing in NetBeans

1. Open NetBeans IDE
2. Go to **Tools > Plugins**
3. Click the **Downloaded** tab
4. Click **Add Plugins...** and select the `.nbm` file
5. Follow the installation wizard
6. Restart NetBeans when prompted

## Usage

### Automatic Startup

The plugin automatically starts when NetBeans launches and:

1. **Creates Lock File**: Writes connection information to `~/.claude/ide/{port}.lock`
2. **Starts WebSocket Server**: Listens on an available port (8990-9100 range)
3. **Updates on Changes**: Refreshes workspace information when projects are opened/closed

### Using with Claude Code

1. **Install Claude Code**: Follow the [official installation guide](https://docs.anthropic.com/en/docs/claude-code/overview)

2. **Start NetBeans**: Open NetBeans with your project

3. **Run Claude Code**: In any terminal, run:
   ```bash
   claude
   ```

4. **Verify Connection**: Claude Code should automatically detect NetBeans. You can verify with:
   ```bash
   /ide
   ```

### Available MCP Tools

The plugin provides these tools to Claude Code:

#### File Operations
- `read_file`: Read file contents
- `write_file`: Write content to files
- `list_files`: List directory contents

#### Project Operations
- `get_open_projects`: List all open projects
- `get_project_files`: Get files in a specific project

#### Editor Operations
- `get_open_documents`: List open documents
- `get_document_content`: Get content from open documents

### Plugin Status

Check the plugin status through **Tools > Claude Code Status** in the NetBeans menu.

## Architecture

### Components

1. **ClaudeCodeInstaller**: Main plugin lifecycle manager
2. **LockFileManager**: Handles lock file creation and updates
3. **WebSocketMCPServer**: WebSocket server for MCP communication
4. **NetBeansMCPHandler**: Processes MCP messages and provides IDE capabilities
5. **MCPWebSocketHandler**: WebSocket message routing

### Communication Flow

```
Claude Code CLI → WebSocket → NetBeans Plugin → NetBeans IDE APIs
                ←           ←                  ←
```

### Lock File Format

```json
{
  "pid": 12345,
  "ideName": "NetBeans",
  "transport": "ws",
  "port": 8991,
  "workspaceFolders": ["/path/to/project"]
}
```

## Development

### Project Structure

```
src/main/java/org/openbeans/claude/netbeans/
├── ClaudeCodeInstaller.java      # Plugin lifecycle
├── LockFileManager.java          # Lock file management
├── WebSocketMCPServer.java       # WebSocket server
├── MCPWebSocketHandler.java      # WebSocket message handler
├── NetBeansMCPHandler.java       # MCP protocol implementation
└── ClaudeCodeAction.java         # Status action

src/main/resources/
├── org/openbeans/claude/netbeans/Bundle.properties
└── META-INF/services/org.openide.modules.ModuleInstall

src/main/nbm/
└── manifest.mf                   # Plugin manifest
```

### Dependencies

- **NetBeans Platform APIs**: IDE integration
- **Model Context Protocol SDK**: MCP implementation
- **Jetty WebSocket**: WebSocket server
- **Jackson**: JSON processing

### Building for Development

```bash
# Build and install in development NetBeans
mvn clean install nbm:run-ide

# Package for distribution
mvn clean package
```

## Troubleshooting

### Plugin Not Loading
- Check NetBeans logs: **View > IDE Log**
- Verify Java 11+ is being used
- Ensure all dependencies are available

### Claude Code Not Connecting
- Verify lock file exists: `~/.claude/ide/{port}.lock`
- Check if WebSocket port is accessible
- Review plugin status: **Tools > Claude Code Status**

### WebSocket Connection Issues
- Check firewall settings for the port range (8990-9100)
- Verify no other applications are using the ports
- Review NetBeans and plugin logs

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

See the LICENSE file for details.

## Support

For issues and questions:
- Check the NetBeans IDE logs
- Review Claude Code documentation
- Create an issue in the project repository
