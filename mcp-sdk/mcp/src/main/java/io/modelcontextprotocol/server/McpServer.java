/*
 * Copyright 2024-2025 the original author or authors.
 */

package io.modelcontextprotocol.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.spec.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.DeafaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Factory class for creating Model Context Protocol (MCP) servers. MCP servers expose
 * tools, resources, and prompts to AI models through a standardized interface.
 *
 * <p>
 * This class serves as the main entry point for implementing the server-side of the MCP
 * specification. The server's responsibilities include:
 * <ul>
 * <li>Exposing tools that models can invoke to perform actions
 * <li>Providing access to resources that give models context
 * <li>Managing prompt templates for structured model interactions
 * <li>Handling client connections and requests
 * <li>Implementing capability negotiation
 * </ul>
 *
 * <p>
 * Thread Safety: Both synchronous and asynchronous server implementations are
 * thread-safe. The synchronous server processes requests sequentially, while the
 * asynchronous server can handle concurrent requests safely through its reactive
 * programming model.
 *
 * <p>
 * Error Handling: The server implementations provide robust error handling through the
 * McpError class. Errors are properly propagated to clients while maintaining the
 * server's stability. Server implementations should use appropriate error codes and
 * provide meaningful error messages to help diagnose issues.
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link McpAsyncServer} for non-blocking operations with reactive responses
 * <li>{@link McpSyncServer} for blocking operations with direct responses
 * </ul>
 *
 * <p>
 * Example of creating a basic synchronous server: <pre>{@code
 * McpServer.sync(transportProvider)
 *     .serverInfo("my-server", "1.0.0")
 *     .tool(new Tool("calculator", "Performs calculations", schema),
 *           (exchange, args) -> new CallToolResult("Result: " + calculate(args)))
 *     .build();
 * }</pre>
 *
 * Example of creating a basic asynchronous server: <pre>{@code
 * McpServer.async(transportProvider)
 *     .serverInfo("my-server", "1.0.0")
 *     .tool(new Tool("calculator", "Performs calculations", schema),
 *           (exchange, args) -> Mono.fromSupplier(() -> calculate(args))
 *               .map(result -> new CallToolResult("Result: " + result)))
 *     .build();
 * }</pre>
 *
 * <p>
 * Example with comprehensive asynchronous configuration: <pre>{@code
 * McpServer.async(transportProvider)
 *     .serverInfo("advanced-server", "2.0.0")
 *     .capabilities(new ServerCapabilities(...))
 *     // Register tools
 *     .tools(
 *         McpServerFeatures.AsyncToolSpecification.builder()
 * 			.tool(calculatorTool)
 *   	    .callTool((exchange, args) -> Mono.fromSupplier(() -> calculate(args.arguments()))
 *                 .map(result -> new CallToolResult("Result: " + result))))
 *.         .build(),
 *         McpServerFeatures.AsyncToolSpecification.builder()
 * 	        .tool((weatherTool)
 *          .callTool((exchange, args) -> Mono.fromSupplier(() -> getWeather(args.arguments()))
 *                 .map(result -> new CallToolResult("Weather: " + result))))
 *          .build()
 *     )
 *     // Register resources
 *     .resources(
 *         new McpServerFeatures.AsyncResourceSpecification(fileResource,
 *             (exchange, req) -> Mono.fromSupplier(() -> readFile(req))
 *                 .map(ReadResourceResult::new)),
 *         new McpServerFeatures.AsyncResourceSpecification(dbResource,
 *             (exchange, req) -> Mono.fromSupplier(() -> queryDb(req))
 *                 .map(ReadResourceResult::new))
 *     )
 *     // Add resource templates
 *     .resourceTemplates(
 *         new ResourceTemplate("file://{path}", "Access files"),
 *         new ResourceTemplate("db://{table}", "Access database")
 *     )
 *     // Register prompts
 *     .prompts(
 *         new McpServerFeatures.AsyncPromptSpecification(analysisPrompt,
 *             (exchange, req) -> Mono.fromSupplier(() -> generateAnalysisPrompt(req))
 *                 .map(GetPromptResult::new)),
 *         new McpServerFeatures.AsyncPromptRegistration(summaryPrompt,
 *             (exchange, req) -> Mono.fromSupplier(() -> generateSummaryPrompt(req))
 *                 .map(GetPromptResult::new))
 *     )
 *     .build();
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 * @author Jihoon Kim
 * @see McpAsyncServer
 * @see McpSyncServer
 * @see McpServerTransportProvider
 */
public interface McpServer {

	/**
	 * Starts building a synchronous MCP server that provides blocking operations.
	 * Synchronous servers block the current Thread's execution upon each request before
	 * giving the control back to the caller, making them simpler to implement but
	 * potentially less scalable for concurrent operations.
	 * @param transportProvider The transport layer implementation for MCP communication.
	 * @return A new instance of {@link SyncSpecification} for configuring the server.
	 */
	static SyncSpecification sync(McpServerTransportProvider transportProvider) {
		return new SyncSpecification(transportProvider);
	}

	/**
	 * Starts building an asynchronous MCP server that provides non-blocking operations.
	 * Asynchronous servers can handle multiple requests concurrently on a single Thread
	 * using a functional paradigm with non-blocking server transports, making them more
	 * scalable for high-concurrency scenarios but more complex to implement.
	 * @param transportProvider The transport layer implementation for MCP communication.
	 * @return A new instance of {@link AsyncSpecification} for configuring the server.
	 */
	static AsyncSpecification async(McpServerTransportProvider transportProvider) {
		return new AsyncSpecification(transportProvider);
	}

	/**
	 * Asynchronous server specification.
	 */
	class AsyncSpecification {

		private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server",
				"1.0.0");

		private final McpServerTransportProvider transportProvider;

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DeafaultMcpUriTemplateManagerFactory();

		private ObjectMapper objectMapper;

		private McpSchema.Implementation serverInfo = DEFAULT_SERVER_INFO;

		private McpSchema.ServerCapabilities serverCapabilities;

		private JsonSchemaValidator jsonSchemaValidator;

		private String instructions;

		/**
		 * The Model Context Protocol (MCP) allows servers to expose tools that can be
		 * invoked by language models. Tools enable models to interact with external
		 * systems, such as querying databases, calling APIs, or performing computations.
		 * Each tool is uniquely identified by a name and includes metadata describing its
		 * schema.
		 */
		private final List<McpServerFeatures.AsyncToolSpecification> tools = new ArrayList<>();

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose resources to clients. Resources allow servers to share data that
		 * provides context to language models, such as files, database schemas, or
		 * application-specific information. Each resource is uniquely identified by a
		 * URI.
		 */
		private final Map<String, McpServerFeatures.AsyncResourceSpecification> resources = new HashMap<>();

		private final List<ResourceTemplate> resourceTemplates = new ArrayList<>();

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose prompt templates to clients. Prompts allow servers to provide structured
		 * messages and instructions for interacting with language models. Clients can
		 * discover available prompts, retrieve their contents, and provide arguments to
		 * customize them.
		 */
		private final Map<String, McpServerFeatures.AsyncPromptSpecification> prompts = new HashMap<>();

		private final Map<McpSchema.CompleteReference, McpServerFeatures.AsyncCompletionSpecification> completions = new HashMap<>();

		private final List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeHandlers = new ArrayList<>();

		private Duration requestTimeout = Duration.ofSeconds(10); // Default timeout

		private AsyncSpecification(McpServerTransportProvider transportProvider) {
			Assert.notNull(transportProvider, "Transport provider must not be null");
			this.transportProvider = transportProvider;
		}

		/**
		 * Sets the URI template manager factory to use for creating URI templates. This
		 * allows for custom URI template parsing and variable extraction.
		 * @param uriTemplateManagerFactory The factory to use. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if uriTemplateManagerFactory is null
		 */
		public AsyncSpecification uriTemplateManagerFactory(McpUriTemplateManagerFactory uriTemplateManagerFactory) {
			Assert.notNull(uriTemplateManagerFactory, "URI template manager factory must not be null");
			this.uriTemplateManagerFactory = uriTemplateManagerFactory;
			return this;
		}

		/**
		 * Sets the duration to wait for server responses before timing out requests. This
		 * timeout applies to all requests made through the client, including tool calls,
		 * resource access, and prompt operations.
		 * @param requestTimeout The duration to wait before timing out requests. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if requestTimeout is null
		 */
		public AsyncSpecification requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		/**
		 * Sets the server implementation information that will be shared with clients
		 * during connection initialization. This helps with version compatibility,
		 * debugging, and server identification.
		 * @param serverInfo The server implementation details including name and version.
		 * Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if serverInfo is null
		 */
		public AsyncSpecification serverInfo(McpSchema.Implementation serverInfo) {
			Assert.notNull(serverInfo, "Server info must not be null");
			this.serverInfo = serverInfo;
			return this;
		}

		/**
		 * Sets the server implementation information using name and version strings. This
		 * is a convenience method alternative to
		 * {@link #serverInfo(McpSchema.Implementation)}.
		 * @param name The server name. Must not be null or empty.
		 * @param version The server version. Must not be null or empty.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if name or version is null or empty
		 * @see #serverInfo(McpSchema.Implementation)
		 */
		public AsyncSpecification serverInfo(String name, String version) {
			Assert.hasText(name, "Name must not be null or empty");
			Assert.hasText(version, "Version must not be null or empty");
			this.serverInfo = new McpSchema.Implementation(name, version);
			return this;
		}

		/**
		 * Sets the server instructions that will be shared with clients during connection
		 * initialization. These instructions provide guidance to the client on how to
		 * interact with this server.
		 * @param instructions The instructions text. Can be null or empty.
		 * @return This builder instance for method chaining
		 */
		public AsyncSpecification instructions(String instructions) {
			this.instructions = instructions;
			return this;
		}

		/**
		 * Sets the server capabilities that will be advertised to clients during
		 * connection initialization. Capabilities define what features the server
		 * supports, such as:
		 * <ul>
		 * <li>Tool execution
		 * <li>Resource access
		 * <li>Prompt handling
		 * </ul>
		 * @param serverCapabilities The server capabilities configuration. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if serverCapabilities is null
		 */
		public AsyncSpecification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
			Assert.notNull(serverCapabilities, "Server capabilities must not be null");
			this.serverCapabilities = serverCapabilities;
			return this;
		}

		/**
		 * Adds a single tool with its implementation handler to the server. This is a
		 * convenience method for registering individual tools without creating a
		 * {@link McpServerFeatures.AsyncToolSpecification} explicitly.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .tool(
		 *     new Tool("calculator", "Performs calculations", schema),
		 *     (exchange, args) -> Mono.fromSupplier(() -> calculate(args))
		 *         .map(result -> new CallToolResult("Result: " + result))
		 * )
		 * }</pre>
		 * @param tool The tool definition including name, description, and schema. Must
		 * not be null.
		 * @param handler The function that implements the tool's logic. Must not be null.
		 * The function's first argument is an {@link McpAsyncServerExchange} upon which
		 * the server can interact with the connected client. The second argument is the
		 * map of arguments passed to the tool.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if tool or handler is null
		 * @deprecated Use {@link #toolCall(McpSchema.Tool, BiFunction)} instead for tool
		 * calls that require a request object.
		 */
		@Deprecated
		public AsyncSpecification tool(McpSchema.Tool tool,
				BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<CallToolResult>> handler) {
			Assert.notNull(tool, "Tool must not be null");
			Assert.notNull(handler, "Handler must not be null");
			assertNoDuplicateTool(tool.name());

			this.tools.add(new McpServerFeatures.AsyncToolSpecification(tool, handler));

			return this;
		}

		/**
		 * Adds a single tool with its implementation handler to the server. This is a
		 * convenience method for registering individual tools without creating a
		 * {@link McpServerFeatures.AsyncToolSpecification} explicitly.
		 * @param tool The tool definition including name, description, and schema. Must
		 * not be null.
		 * @param callHandler The function that implements the tool's logic. Must not be
		 * null. The function's first argument is an {@link McpAsyncServerExchange} upon
		 * which the server can interact with the connected client. The second argument is
		 * the {@link McpSchema.CallToolRequest} object containing the tool call
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if tool or handler is null
		 */
		public AsyncSpecification toolCall(McpSchema.Tool tool,
				BiFunction<McpAsyncServerExchange, McpSchema.CallToolRequest, Mono<CallToolResult>> callHandler) {

			Assert.notNull(tool, "Tool must not be null");
			Assert.notNull(callHandler, "Handler must not be null");
			assertNoDuplicateTool(tool.name());

			this.tools
				.add(McpServerFeatures.AsyncToolSpecification.builder().tool(tool).callHandler(callHandler).build());

			return this;
		}

		/**
		 * Adds multiple tools with their handlers to the server using a List. This method
		 * is useful when tools are dynamically generated or loaded from a configuration
		 * source.
		 * @param toolSpecifications The list of tool specifications to add. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if toolSpecifications is null
		 * @see #tools(McpServerFeatures.AsyncToolSpecification...)
		 */
		public AsyncSpecification tools(List<McpServerFeatures.AsyncToolSpecification> toolSpecifications) {
			Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

			for (var tool : toolSpecifications) {
				assertNoDuplicateTool(tool.tool().name());
				this.tools.add(tool);
			}

			return this;
		}

		/**
		 * Adds multiple tools with their handlers to the server using varargs. This
		 * method provides a convenient way to register multiple tools inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .tools(
		 *     McpServerFeatures.AsyncToolSpecification.builder().tool(calculatorTool).callTool(calculatorHandler).build(),
		 *     McpServerFeatures.AsyncToolSpecification.builder().tool(weatherTool).callTool(weatherHandler).build(),
		 *     McpServerFeatures.AsyncToolSpecification.builder().tool(fileManagerTool).callTool(fileManagerHandler).build()
		 * )
		 * }</pre>
		 * @param toolSpecifications The tool specifications to add. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if toolSpecifications is null
		 */
		public AsyncSpecification tools(McpServerFeatures.AsyncToolSpecification... toolSpecifications) {
			Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

			for (McpServerFeatures.AsyncToolSpecification tool : toolSpecifications) {
				assertNoDuplicateTool(tool.tool().name());
				this.tools.add(tool);
			}
			return this;
		}

		private void assertNoDuplicateTool(String toolName) {
			if (this.tools.stream().anyMatch(toolSpec -> toolSpec.tool().name().equals(toolName))) {
				throw new IllegalArgumentException("Tool with name '" + toolName + "' is already registered.");
			}
		}

		/**
		 * Registers multiple resources with their handlers using a Map. This method is
		 * useful when resources are dynamically generated or loaded from a configuration
		 * source.
		 * @param resourceSpecifications Map of resource name to specification. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 * @see #resources(McpServerFeatures.AsyncResourceSpecification...)
		 */
		public AsyncSpecification resources(
				Map<String, McpServerFeatures.AsyncResourceSpecification> resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers map must not be null");
			this.resources.putAll(resourceSpecifications);
			return this;
		}

		/**
		 * Registers multiple resources with their handlers using a List. This method is
		 * useful when resources need to be added in bulk from a collection.
		 * @param resourceSpecifications List of resource specifications. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 * @see #resources(McpServerFeatures.AsyncResourceSpecification...)
		 */
		public AsyncSpecification resources(List<McpServerFeatures.AsyncResourceSpecification> resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
			for (McpServerFeatures.AsyncResourceSpecification resource : resourceSpecifications) {
				this.resources.put(resource.resource().uri(), resource);
			}
			return this;
		}

		/**
		 * Registers multiple resources with their handlers using varargs. This method
		 * provides a convenient way to register multiple resources inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .resources(
		 *     new McpServerFeatures.AsyncResourceSpecification(fileResource, fileHandler),
		 *     new McpServerFeatures.AsyncResourceSpecification(dbResource, dbHandler),
		 *     new McpServerFeatures.AsyncResourceSpecification(apiResource, apiHandler)
		 * )
		 * }</pre>
		 * @param resourceSpecifications The resource specifications to add. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 */
		public AsyncSpecification resources(McpServerFeatures.AsyncResourceSpecification... resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
			for (McpServerFeatures.AsyncResourceSpecification resource : resourceSpecifications) {
				this.resources.put(resource.resource().uri(), resource);
			}
			return this;
		}

		/**
		 * Sets the resource templates that define patterns for dynamic resource access.
		 * Templates use URI patterns with placeholders that can be filled at runtime.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .resourceTemplates(
		 *     new ResourceTemplate("file://{path}", "Access files by path"),
		 *     new ResourceTemplate("db://{table}/{id}", "Access database records")
		 * )
		 * }</pre>
		 * @param resourceTemplates List of resource templates. If null, clears existing
		 * templates.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceTemplates is null.
		 * @see #resourceTemplates(ResourceTemplate...)
		 */
		public AsyncSpecification resourceTemplates(List<ResourceTemplate> resourceTemplates) {
			Assert.notNull(resourceTemplates, "Resource templates must not be null");
			this.resourceTemplates.addAll(resourceTemplates);
			return this;
		}

		/**
		 * Sets the resource templates using varargs for convenience. This is an
		 * alternative to {@link #resourceTemplates(List)}.
		 * @param resourceTemplates The resource templates to set.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceTemplates is null.
		 * @see #resourceTemplates(List)
		 */
		public AsyncSpecification resourceTemplates(ResourceTemplate... resourceTemplates) {
			Assert.notNull(resourceTemplates, "Resource templates must not be null");
			for (ResourceTemplate resourceTemplate : resourceTemplates) {
				this.resourceTemplates.add(resourceTemplate);
			}
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using a Map. This method is
		 * useful when prompts are dynamically generated or loaded from a configuration
		 * source.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .prompts(Map.of("analysis", new McpServerFeatures.AsyncPromptSpecification(
		 *     new Prompt("analysis", "Code analysis template"),
		 *     request -> Mono.fromSupplier(() -> generateAnalysisPrompt(request))
		 *         .map(GetPromptResult::new)
		 * )));
		 * }</pre>
		 * @param prompts Map of prompt name to specification. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 */
		public AsyncSpecification prompts(Map<String, McpServerFeatures.AsyncPromptSpecification> prompts) {
			Assert.notNull(prompts, "Prompts map must not be null");
			this.prompts.putAll(prompts);
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using a List. This method is
		 * useful when prompts need to be added in bulk from a collection.
		 * @param prompts List of prompt specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 * @see #prompts(McpServerFeatures.AsyncPromptSpecification...)
		 */
		public AsyncSpecification prompts(List<McpServerFeatures.AsyncPromptSpecification> prompts) {
			Assert.notNull(prompts, "Prompts list must not be null");
			for (McpServerFeatures.AsyncPromptSpecification prompt : prompts) {
				this.prompts.put(prompt.prompt().name(), prompt);
			}
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using varargs. This method
		 * provides a convenient way to register multiple prompts inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .prompts(
		 *     new McpServerFeatures.AsyncPromptSpecification(analysisPrompt, analysisHandler),
		 *     new McpServerFeatures.AsyncPromptSpecification(summaryPrompt, summaryHandler),
		 *     new McpServerFeatures.AsyncPromptSpecification(reviewPrompt, reviewHandler)
		 * )
		 * }</pre>
		 * @param prompts The prompt specifications to add. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 */
		public AsyncSpecification prompts(McpServerFeatures.AsyncPromptSpecification... prompts) {
			Assert.notNull(prompts, "Prompts list must not be null");
			for (McpServerFeatures.AsyncPromptSpecification prompt : prompts) {
				this.prompts.put(prompt.prompt().name(), prompt);
			}
			return this;
		}

		/**
		 * Registers multiple completions with their handlers using a List. This method is
		 * useful when completions need to be added in bulk from a collection.
		 * @param completions List of completion specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if completions is null
		 */
		public AsyncSpecification completions(List<McpServerFeatures.AsyncCompletionSpecification> completions) {
			Assert.notNull(completions, "Completions list must not be null");
			for (McpServerFeatures.AsyncCompletionSpecification completion : completions) {
				this.completions.put(completion.referenceKey(), completion);
			}
			return this;
		}

		/**
		 * Registers multiple completions with their handlers using varargs. This method
		 * is useful when completions are defined inline and added directly.
		 * @param completions Array of completion specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if completions is null
		 */
		public AsyncSpecification completions(McpServerFeatures.AsyncCompletionSpecification... completions) {
			Assert.notNull(completions, "Completions list must not be null");
			for (McpServerFeatures.AsyncCompletionSpecification completion : completions) {
				this.completions.put(completion.referenceKey(), completion);
			}
			return this;
		}

		/**
		 * Registers a consumer that will be notified when the list of roots changes. This
		 * is useful for updating resource availability dynamically, such as when new
		 * files are added or removed.
		 * @param handler The handler to register. Must not be null. The function's first
		 * argument is an {@link McpAsyncServerExchange} upon which the server can
		 * interact with the connected client. The second argument is the list of roots.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumer is null
		 */
		public AsyncSpecification rootsChangeHandler(
				BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>> handler) {
			Assert.notNull(handler, "Consumer must not be null");
			this.rootsChangeHandlers.add(handler);
			return this;
		}

		/**
		 * Registers multiple consumers that will be notified when the list of roots
		 * changes. This method is useful when multiple consumers need to be registered at
		 * once.
		 * @param handlers The list of handlers to register. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumers is null
		 * @see #rootsChangeHandler(BiFunction)
		 */
		public AsyncSpecification rootsChangeHandlers(
				List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> handlers) {
			Assert.notNull(handlers, "Handlers list must not be null");
			this.rootsChangeHandlers.addAll(handlers);
			return this;
		}

		/**
		 * Registers multiple consumers that will be notified when the list of roots
		 * changes using varargs. This method provides a convenient way to register
		 * multiple consumers inline.
		 * @param handlers The handlers to register. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumers is null
		 * @see #rootsChangeHandlers(List)
		 */
		public AsyncSpecification rootsChangeHandlers(
				@SuppressWarnings("unchecked") BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>... handlers) {
			Assert.notNull(handlers, "Handlers list must not be null");
			return this.rootsChangeHandlers(Arrays.asList(handlers));
		}

		/**
		 * Sets the object mapper to use for serializing and deserializing JSON messages.
		 * @param objectMapper the instance to use. Must not be null.
		 * @return This builder instance for method chaining.
		 * @throws IllegalArgumentException if objectMapper is null
		 */
		public AsyncSpecification objectMapper(ObjectMapper objectMapper) {
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			this.objectMapper = objectMapper;
			return this;
		}

		/**
		 * Sets the JSON schema validator to use for validating tool and resource schemas.
		 * This ensures that the server's tools and resources conform to the expected
		 * schema definitions.
		 * @param jsonSchemaValidator The validator to use. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if jsonSchemaValidator is null
		 */
		public AsyncSpecification jsonSchemaValidator(JsonSchemaValidator jsonSchemaValidator) {
			Assert.notNull(jsonSchemaValidator, "JsonSchemaValidator must not be null");
			this.jsonSchemaValidator = jsonSchemaValidator;
			return this;
		}

		/**
		 * Builds an asynchronous MCP server that provides non-blocking operations.
		 * @return A new instance of {@link McpAsyncServer} configured with this builder's
		 * settings.
		 */
		public McpAsyncServer build() {
			var features = new McpServerFeatures.Async(this.serverInfo, this.serverCapabilities, this.tools,
					this.resources, this.resourceTemplates, this.prompts, this.completions, this.rootsChangeHandlers,
					this.instructions);
			var mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
			var jsonSchemaValidator = this.jsonSchemaValidator != null ? this.jsonSchemaValidator
					: new DefaultJsonSchemaValidator(mapper);
			return new McpAsyncServer(this.transportProvider, mapper, features, this.requestTimeout,
					this.uriTemplateManagerFactory, jsonSchemaValidator);
		}

	}

	/**
	 * Synchronous server specification.
	 */
	class SyncSpecification {

		private static final McpSchema.Implementation DEFAULT_SERVER_INFO = new McpSchema.Implementation("mcp-server",
				"1.0.0");

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DeafaultMcpUriTemplateManagerFactory();

		private final McpServerTransportProvider transportProvider;

		private ObjectMapper objectMapper;

		private McpSchema.Implementation serverInfo = DEFAULT_SERVER_INFO;

		private McpSchema.ServerCapabilities serverCapabilities;

		private String instructions;

		/**
		 * The Model Context Protocol (MCP) allows servers to expose tools that can be
		 * invoked by language models. Tools enable models to interact with external
		 * systems, such as querying databases, calling APIs, or performing computations.
		 * Each tool is uniquely identified by a name and includes metadata describing its
		 * schema.
		 */
		private final List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose resources to clients. Resources allow servers to share data that
		 * provides context to language models, such as files, database schemas, or
		 * application-specific information. Each resource is uniquely identified by a
		 * URI.
		 */
		private final Map<String, McpServerFeatures.SyncResourceSpecification> resources = new HashMap<>();

		private final List<ResourceTemplate> resourceTemplates = new ArrayList<>();

		private JsonSchemaValidator jsonSchemaValidator;

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose prompt templates to clients. Prompts allow servers to provide structured
		 * messages and instructions for interacting with language models. Clients can
		 * discover available prompts, retrieve their contents, and provide arguments to
		 * customize them.
		 */
		private final Map<String, McpServerFeatures.SyncPromptSpecification> prompts = new HashMap<>();

		private final Map<McpSchema.CompleteReference, McpServerFeatures.SyncCompletionSpecification> completions = new HashMap<>();

		private final List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeHandlers = new ArrayList<>();

		private Duration requestTimeout = Duration.ofSeconds(10); // Default timeout

		private boolean immediateExecution = false;

		private SyncSpecification(McpServerTransportProvider transportProvider) {
			Assert.notNull(transportProvider, "Transport provider must not be null");
			this.transportProvider = transportProvider;
		}

		/**
		 * Sets the URI template manager factory to use for creating URI templates. This
		 * allows for custom URI template parsing and variable extraction.
		 * @param uriTemplateManagerFactory The factory to use. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if uriTemplateManagerFactory is null
		 */
		public SyncSpecification uriTemplateManagerFactory(McpUriTemplateManagerFactory uriTemplateManagerFactory) {
			Assert.notNull(uriTemplateManagerFactory, "URI template manager factory must not be null");
			this.uriTemplateManagerFactory = uriTemplateManagerFactory;
			return this;
		}

		/**
		 * Sets the duration to wait for server responses before timing out requests. This
		 * timeout applies to all requests made through the client, including tool calls,
		 * resource access, and prompt operations.
		 * @param requestTimeout The duration to wait before timing out requests. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if requestTimeout is null
		 */
		public SyncSpecification requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		/**
		 * Sets the server implementation information that will be shared with clients
		 * during connection initialization. This helps with version compatibility,
		 * debugging, and server identification.
		 * @param serverInfo The server implementation details including name and version.
		 * Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if serverInfo is null
		 */
		public SyncSpecification serverInfo(McpSchema.Implementation serverInfo) {
			Assert.notNull(serverInfo, "Server info must not be null");
			this.serverInfo = serverInfo;
			return this;
		}

		/**
		 * Sets the server implementation information using name and version strings. This
		 * is a convenience method alternative to
		 * {@link #serverInfo(McpSchema.Implementation)}.
		 * @param name The server name. Must not be null or empty.
		 * @param version The server version. Must not be null or empty.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if name or version is null or empty
		 * @see #serverInfo(McpSchema.Implementation)
		 */
		public SyncSpecification serverInfo(String name, String version) {
			Assert.hasText(name, "Name must not be null or empty");
			Assert.hasText(version, "Version must not be null or empty");
			this.serverInfo = new McpSchema.Implementation(name, version);
			return this;
		}

		/**
		 * Sets the server instructions that will be shared with clients during connection
		 * initialization. These instructions provide guidance to the client on how to
		 * interact with this server.
		 * @param instructions The instructions text. Can be null or empty.
		 * @return This builder instance for method chaining
		 */
		public SyncSpecification instructions(String instructions) {
			this.instructions = instructions;
			return this;
		}

		/**
		 * Sets the server capabilities that will be advertised to clients during
		 * connection initialization. Capabilities define what features the server
		 * supports, such as:
		 * <ul>
		 * <li>Tool execution
		 * <li>Resource access
		 * <li>Prompt handling
		 * </ul>
		 * @param serverCapabilities The server capabilities configuration. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if serverCapabilities is null
		 */
		public SyncSpecification capabilities(McpSchema.ServerCapabilities serverCapabilities) {
			Assert.notNull(serverCapabilities, "Server capabilities must not be null");
			this.serverCapabilities = serverCapabilities;
			return this;
		}

		/**
		 * Adds a single tool with its implementation handler to the server. This is a
		 * convenience method for registering individual tools without creating a
		 * {@link McpServerFeatures.SyncToolSpecification} explicitly.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .tool(
		 *     new Tool("calculator", "Performs calculations", schema),
		 *     (exchange, args) -> new CallToolResult("Result: " + calculate(args))
		 * )
		 * }</pre>
		 * @param tool The tool definition including name, description, and schema. Must
		 * not be null.
		 * @param handler The function that implements the tool's logic. Must not be null.
		 * The function's first argument is an {@link McpSyncServerExchange} upon which
		 * the server can interact with the connected client. The second argument is the
		 * list of arguments passed to the tool.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if tool or handler is null
		 * @deprecated Use {@link #toolCall(McpSchema.Tool, BiFunction)} instead for tool
		 * calls that require a request object.
		 */
		@Deprecated
		public SyncSpecification tool(McpSchema.Tool tool,
				BiFunction<McpSyncServerExchange, Map<String, Object>, CallToolResult> handler) {
			Assert.notNull(tool, "Tool must not be null");
			Assert.notNull(handler, "Handler must not be null");
			assertNoDuplicateTool(tool.name());

			this.tools.add(new McpServerFeatures.SyncToolSpecification(tool, handler));

			return this;
		}

		/**
		 * Adds a single tool with its implementation handler to the server. This is a
		 * convenience method for registering individual tools without creating a
		 * {@link McpServerFeatures.SyncToolSpecification} explicitly.
		 * @param tool The tool definition including name, description, and schema. Must
		 * not be null.
		 * @param handler The function that implements the tool's logic. Must not be null.
		 * The function's first argument is an {@link McpSyncServerExchange} upon which
		 * the server can interact with the connected client. The second argument is the
		 * list of arguments passed to the tool.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if tool or handler is null
		 */
		public SyncSpecification toolCall(McpSchema.Tool tool,
				BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, CallToolResult> handler) {
			Assert.notNull(tool, "Tool must not be null");
			Assert.notNull(handler, "Handler must not be null");
			assertNoDuplicateTool(tool.name());

			this.tools.add(new McpServerFeatures.SyncToolSpecification(tool, null, handler));

			return this;
		}

		/**
		 * Adds multiple tools with their handlers to the server using a List. This method
		 * is useful when tools are dynamically generated or loaded from a configuration
		 * source.
		 * @param toolSpecifications The list of tool specifications to add. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if toolSpecifications is null
		 * @see #tools(McpServerFeatures.SyncToolSpecification...)
		 */
		public SyncSpecification tools(List<McpServerFeatures.SyncToolSpecification> toolSpecifications) {
			Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

			for (var tool : toolSpecifications) {
				String toolName = tool.tool().name();
				assertNoDuplicateTool(toolName); // Check against existing tools
				this.tools.add(tool);
			}

			return this;
		}

		/**
		 * Adds multiple tools with their handlers to the server using varargs. This
		 * method provides a convenient way to register multiple tools inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .tools(
		 *     new ToolSpecification(calculatorTool, calculatorHandler),
		 *     new ToolSpecification(weatherTool, weatherHandler),
		 *     new ToolSpecification(fileManagerTool, fileManagerHandler)
		 * )
		 * }</pre>
		 * @param toolSpecifications The tool specifications to add. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if toolSpecifications is null
		 * @see #tools(List)
		 */
		public SyncSpecification tools(McpServerFeatures.SyncToolSpecification... toolSpecifications) {
			Assert.notNull(toolSpecifications, "Tool handlers list must not be null");

			for (McpServerFeatures.SyncToolSpecification tool : toolSpecifications) {
				assertNoDuplicateTool(tool.tool().name());
				this.tools.add(tool);
			}
			return this;
		}

		private void assertNoDuplicateTool(String toolName) {
			if (this.tools.stream().anyMatch(toolSpec -> toolSpec.tool().name().equals(toolName))) {
				throw new IllegalArgumentException("Tool with name '" + toolName + "' is already registered.");
			}
		}

		/**
		 * Registers multiple resources with their handlers using a Map. This method is
		 * useful when resources are dynamically generated or loaded from a configuration
		 * source.
		 * @param resourceSpecifications Map of resource name to specification. Must not
		 * be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 * @see #resources(McpServerFeatures.SyncResourceSpecification...)
		 */
		public SyncSpecification resources(
				Map<String, McpServerFeatures.SyncResourceSpecification> resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers map must not be null");
			this.resources.putAll(resourceSpecifications);
			return this;
		}

		/**
		 * Registers multiple resources with their handlers using a List. This method is
		 * useful when resources need to be added in bulk from a collection.
		 * @param resourceSpecifications List of resource specifications. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 * @see #resources(McpServerFeatures.SyncResourceSpecification...)
		 */
		public SyncSpecification resources(List<McpServerFeatures.SyncResourceSpecification> resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
			for (McpServerFeatures.SyncResourceSpecification resource : resourceSpecifications) {
				this.resources.put(resource.resource().uri(), resource);
			}
			return this;
		}

		/**
		 * Registers multiple resources with their handlers using varargs. This method
		 * provides a convenient way to register multiple resources inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .resources(
		 *     new ResourceSpecification(fileResource, fileHandler),
		 *     new ResourceSpecification(dbResource, dbHandler),
		 *     new ResourceSpecification(apiResource, apiHandler)
		 * )
		 * }</pre>
		 * @param resourceSpecifications The resource specifications to add. Must not be
		 * null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceSpecifications is null
		 */
		public SyncSpecification resources(McpServerFeatures.SyncResourceSpecification... resourceSpecifications) {
			Assert.notNull(resourceSpecifications, "Resource handlers list must not be null");
			for (McpServerFeatures.SyncResourceSpecification resource : resourceSpecifications) {
				this.resources.put(resource.resource().uri(), resource);
			}
			return this;
		}

		/**
		 * Sets the resource templates that define patterns for dynamic resource access.
		 * Templates use URI patterns with placeholders that can be filled at runtime.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .resourceTemplates(
		 *     new ResourceTemplate("file://{path}", "Access files by path"),
		 *     new ResourceTemplate("db://{table}/{id}", "Access database records")
		 * )
		 * }</pre>
		 * @param resourceTemplates List of resource templates. If null, clears existing
		 * templates.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceTemplates is null.
		 * @see #resourceTemplates(ResourceTemplate...)
		 */
		public SyncSpecification resourceTemplates(List<ResourceTemplate> resourceTemplates) {
			Assert.notNull(resourceTemplates, "Resource templates must not be null");
			this.resourceTemplates.addAll(resourceTemplates);
			return this;
		}

		/**
		 * Sets the resource templates using varargs for convenience. This is an
		 * alternative to {@link #resourceTemplates(List)}.
		 * @param resourceTemplates The resource templates to set.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if resourceTemplates is null
		 * @see #resourceTemplates(List)
		 */
		public SyncSpecification resourceTemplates(ResourceTemplate... resourceTemplates) {
			Assert.notNull(resourceTemplates, "Resource templates must not be null");
			for (ResourceTemplate resourceTemplate : resourceTemplates) {
				this.resourceTemplates.add(resourceTemplate);
			}
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using a Map. This method is
		 * useful when prompts are dynamically generated or loaded from a configuration
		 * source.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * Map<String, PromptSpecification> prompts = new HashMap<>();
		 * prompts.put("analysis", new PromptSpecification(
		 *     new Prompt("analysis", "Code analysis template"),
		 *     (exchange, request) -> new GetPromptResult(generateAnalysisPrompt(request))
		 * ));
		 * .prompts(prompts)
		 * }</pre>
		 * @param prompts Map of prompt name to specification. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 */
		public SyncSpecification prompts(Map<String, McpServerFeatures.SyncPromptSpecification> prompts) {
			Assert.notNull(prompts, "Prompts map must not be null");
			this.prompts.putAll(prompts);
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using a List. This method is
		 * useful when prompts need to be added in bulk from a collection.
		 * @param prompts List of prompt specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 * @see #prompts(McpServerFeatures.SyncPromptSpecification...)
		 */
		public SyncSpecification prompts(List<McpServerFeatures.SyncPromptSpecification> prompts) {
			Assert.notNull(prompts, "Prompts list must not be null");
			for (McpServerFeatures.SyncPromptSpecification prompt : prompts) {
				this.prompts.put(prompt.prompt().name(), prompt);
			}
			return this;
		}

		/**
		 * Registers multiple prompts with their handlers using varargs. This method
		 * provides a convenient way to register multiple prompts inline.
		 *
		 * <p>
		 * Example usage: <pre>{@code
		 * .prompts(
		 *     new PromptSpecification(analysisPrompt, analysisHandler),
		 *     new PromptSpecification(summaryPrompt, summaryHandler),
		 *     new PromptSpecification(reviewPrompt, reviewHandler)
		 * )
		 * }</pre>
		 * @param prompts The prompt specifications to add. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if prompts is null
		 */
		public SyncSpecification prompts(McpServerFeatures.SyncPromptSpecification... prompts) {
			Assert.notNull(prompts, "Prompts list must not be null");
			for (McpServerFeatures.SyncPromptSpecification prompt : prompts) {
				this.prompts.put(prompt.prompt().name(), prompt);
			}
			return this;
		}

		/**
		 * Registers multiple completions with their handlers using a List. This method is
		 * useful when completions need to be added in bulk from a collection.
		 * @param completions List of completion specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if completions is null
		 * @see #completions(McpServerFeatures.SyncCompletionSpecification...)
		 */
		public SyncSpecification completions(List<McpServerFeatures.SyncCompletionSpecification> completions) {
			Assert.notNull(completions, "Completions list must not be null");
			for (McpServerFeatures.SyncCompletionSpecification completion : completions) {
				this.completions.put(completion.referenceKey(), completion);
			}
			return this;
		}

		/**
		 * Registers multiple completions with their handlers using varargs. This method
		 * is useful when completions are defined inline and added directly.
		 * @param completions Array of completion specifications. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if completions is null
		 */
		public SyncSpecification completions(McpServerFeatures.SyncCompletionSpecification... completions) {
			Assert.notNull(completions, "Completions list must not be null");
			for (McpServerFeatures.SyncCompletionSpecification completion : completions) {
				this.completions.put(completion.referenceKey(), completion);
			}
			return this;
		}

		/**
		 * Registers a consumer that will be notified when the list of roots changes. This
		 * is useful for updating resource availability dynamically, such as when new
		 * files are added or removed.
		 * @param handler The handler to register. Must not be null. The function's first
		 * argument is an {@link McpSyncServerExchange} upon which the server can interact
		 * with the connected client. The second argument is the list of roots.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumer is null
		 */
		public SyncSpecification rootsChangeHandler(BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> handler) {
			Assert.notNull(handler, "Consumer must not be null");
			this.rootsChangeHandlers.add(handler);
			return this;
		}

		/**
		 * Registers multiple consumers that will be notified when the list of roots
		 * changes. This method is useful when multiple consumers need to be registered at
		 * once.
		 * @param handlers The list of handlers to register. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumers is null
		 * @see #rootsChangeHandler(BiConsumer)
		 */
		public SyncSpecification rootsChangeHandlers(
				List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> handlers) {
			Assert.notNull(handlers, "Handlers list must not be null");
			this.rootsChangeHandlers.addAll(handlers);
			return this;
		}

		/**
		 * Registers multiple consumers that will be notified when the list of roots
		 * changes using varargs. This method provides a convenient way to register
		 * multiple consumers inline.
		 * @param handlers The handlers to register. Must not be null.
		 * @return This builder instance for method chaining
		 * @throws IllegalArgumentException if consumers is null
		 * @see #rootsChangeHandlers(List)
		 */
		public SyncSpecification rootsChangeHandlers(
				BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>... handlers) {
			Assert.notNull(handlers, "Handlers list must not be null");
			return this.rootsChangeHandlers(List.of(handlers));
		}

		/**
		 * Sets the object mapper to use for serializing and deserializing JSON messages.
		 * @param objectMapper the instance to use. Must not be null.
		 * @return This builder instance for method chaining.
		 * @throws IllegalArgumentException if objectMapper is null
		 */
		public SyncSpecification objectMapper(ObjectMapper objectMapper) {
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			this.objectMapper = objectMapper;
			return this;
		}

		public SyncSpecification jsonSchemaValidator(JsonSchemaValidator jsonSchemaValidator) {
			Assert.notNull(jsonSchemaValidator, "JsonSchemaValidator must not be null");
			this.jsonSchemaValidator = jsonSchemaValidator;
			return this;
		}

		/**
		 * Enable on "immediate execution" of the operations on the underlying
		 * {@link McpAsyncServer}. Defaults to false, which does blocking code offloading
		 * to prevent accidental blocking of the non-blocking transport.
		 * <p>
		 * Do NOT set to true if the underlying transport is a non-blocking
		 * implementation.
		 * @param immediateExecution When true, do not offload work asynchronously.
		 * @return This builder instance for method chaining.
		 *
		 */
		public SyncSpecification immediateExecution(boolean immediateExecution) {
			this.immediateExecution = immediateExecution;
			return this;
		}

		/**
		 * Builds a synchronous MCP server that provides blocking operations.
		 * @return A new instance of {@link McpSyncServer} configured with this builder's
		 * settings.
		 */
		public McpSyncServer build() {
			McpServerFeatures.Sync syncFeatures = new McpServerFeatures.Sync(this.serverInfo, this.serverCapabilities,
					this.tools, this.resources, this.resourceTemplates, this.prompts, this.completions,
					this.rootsChangeHandlers, this.instructions);
			McpServerFeatures.Async asyncFeatures = McpServerFeatures.Async.fromSync(syncFeatures,
					this.immediateExecution);
			var mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
			var jsonSchemaValidator = this.jsonSchemaValidator != null ? this.jsonSchemaValidator
					: new DefaultJsonSchemaValidator(mapper);

			var asyncServer = new McpAsyncServer(this.transportProvider, mapper, asyncFeatures, this.requestTimeout,
					this.uriTemplateManagerFactory, jsonSchemaValidator);

			return new McpSyncServer(asyncServer, this.immediateExecution);
		}

	}

}
