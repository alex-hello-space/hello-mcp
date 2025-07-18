# HELLO-MCP
# What is MCP?
MCP is an open protocol that standardizes how applications provide context to LLMs. 
MCP provides a standardized way to connect AI models to **different data sources and tools**.
```mermaid
flowchart LR
    subgraph "Your Computer"
        Host["Host with MCP Client<br/>(Claude, IDEs, Tools)"]
        S1["MCP Server A"]
        S2["MCP Server B"]
        D1[("Local<br/>Data Source A")]
        Host <-->|"MCP Protocol"| S1
        Host <-->|"MCP Protocol"| S2
        S1 <--> D1
    end
    subgraph "Internet"
        S3["MCP Server C"]
        D2[("Remote<br/>Service B")]
        D3[("Remote<br/>Service C")]
        S2 <-->|"Web APIs"| D2
        S3 <-->|"Web APIs"| D3
    end
    Host <-->|"MCP Protocol"| S3
```
* **MCP Hosts**: Programs like Claude Desktop, IDEs, or AI tools that want to access data through MCP
* **MCP Clients**: Protocol clients that maintain 1:1 connections with servers
* **MCP Servers**: Lightweight programs that each expose specific capabilities through the standardized Model Context Protocol
* **Local Data Sources**: Your computer's files, databases, and services that MCP servers can securely access
* **Remote Services**: External systems available over the internet (e.g., through APIs) that MCP servers can connect to

# MCP Features
## Client
### Elicitation
> mcp client的elicitation机制，使mcp server在其已有功能的运行中，还可以内嵌地请求client收集用户输入信息，来完成更复杂的交互逻辑。
