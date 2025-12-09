package com.z254.butterfly.common.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client interface for CORTEX AI Agent service.
 * 
 * <p>CORTEX provides AI agent orchestration capabilities:
 * <ul>
 *   <li>Agent task execution and management</li>
 *   <li>Conversation/chat interfaces</li>
 *   <li>Tool calling and function execution</li>
 *   <li>LLM provider abstraction</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public interface CortexClient extends ButterflyServiceClient {
    
    @Override
    default String getServiceId() {
        return "cortex";
    }
    
    // === Agent Tasks ===
    
    /**
     * Submit a task for agent execution.
     * 
     * @param request Task request with prompt and parameters
     * @return Task result with completion or streaming updates
     */
    Mono<AgentTaskResult> submitTask(AgentTaskRequest request);
    
    /**
     * Get task status and result.
     * 
     * @param taskId Task identifier
     * @return Current task status and result
     */
    Mono<AgentTaskResult> getTask(String taskId);
    
    /**
     * Cancel a running task.
     * 
     * @param taskId Task to cancel
     * @return Void on success
     */
    Mono<Void> cancelTask(String taskId);
    
    /**
     * Stream task execution thoughts.
     * 
     * @param taskId Task identifier
     * @return Stream of agent thoughts during execution
     */
    Flux<AgentThought> streamThoughts(String taskId);
    
    // === Conversations ===
    
    /**
     * Create a new conversation.
     * 
     * @param request Conversation creation parameters
     * @return Created conversation
     */
    Mono<Conversation> createConversation(ConversationRequest request);
    
    /**
     * Get an existing conversation.
     * 
     * @param conversationId Conversation identifier
     * @return Conversation with message history
     */
    Mono<Conversation> getConversation(String conversationId);
    
    /**
     * Send a message to a conversation.
     * 
     * @param conversationId Target conversation
     * @param message Message content
     * @return Agent response message
     */
    Mono<Message> sendMessage(String conversationId, String message);
    
    /**
     * Stream a response from the agent.
     * 
     * @param conversationId Target conversation
     * @param message Message content
     * @return Stream of response tokens
     */
    Flux<String> streamMessage(String conversationId, String message);
    
    // === Tools ===
    
    /**
     * List available tools for agents.
     * 
     * @return List of available tools
     */
    Flux<AgentTool> listTools();
    
    /**
     * Execute a tool call.
     * 
     * @param toolId Tool identifier
     * @param parameters Tool parameters
     * @return Tool execution result
     */
    Mono<ToolResult> executeTool(String toolId, Map<String, Object> parameters);
    
    // === DTOs ===
    
    record AgentTaskRequest(
            String prompt,
            String agentType,
            Map<String, Object> context,
            List<String> enabledTools,
            int maxIterations,
            String governancePolicy
    ) {
        public static AgentTaskRequest of(String prompt) {
            return new AgentTaskRequest(prompt, "default", null, null, 10, null);
        }
    }
    
    record AgentTaskResult(
            String taskId,
            String status,
            String result,
            List<String> toolCalls,
            long durationMs,
            Map<String, Object> metadata
    ) {}
    
    record AgentThought(
            String taskId,
            String type,
            String content,
            long timestamp
    ) {}
    
    record ConversationRequest(
            String title,
            String systemPrompt,
            String agentType,
            Map<String, Object> context
    ) {}
    
    record Conversation(
            String id,
            String title,
            List<Message> messages,
            String status,
            long createdAt,
            long updatedAt
    ) {}
    
    record Message(
            String id,
            String role,
            String content,
            long timestamp,
            Map<String, Object> metadata
    ) {}
    
    record AgentTool(
            String id,
            String name,
            String description,
            Map<String, Object> parameters,
            boolean requiresApproval
    ) {}
    
    record ToolResult(
            String toolId,
            boolean success,
            Object result,
            String error,
            long durationMs
    ) {}
}
