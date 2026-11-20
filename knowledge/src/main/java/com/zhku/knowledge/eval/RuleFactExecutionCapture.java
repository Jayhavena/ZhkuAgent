package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.ruleagent.RuleAgentContract.TaskSnapshot;
import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import com.zhku.knowledge.ruleagent.RuleAgentRunTrace;
import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;

/** Host-side capture from the accepted publication, not from provider draft text. Local controlled output only. */
public final class RuleFactExecutionCapture {
    private RuleFactExecutionCapture() {}
    public record Bundle(Execution execution, Object publishedResult, List<RuleAgentRunner.Event> observations) {}

    public static Bundle capture(Identity identity, TaskSnapshot snapshot, List<RuleAgentRunner.Event> events) {
        Objects.requireNonNull(snapshot); events = List.copyOf(events);
        if (!snapshot.terminated() || snapshot.result() == null
                || !identity.taskId().equals(snapshot.taskId()) || !identity.runId().equals(snapshot.runId())
                || !snapshot.runId().equals(snapshot.result().originatingRunId()))
            throw new IllegalArgumentException("accepted terminal publication with matching identity required");
        // Retain exactly the published claims, gap guidance and clarification; citations remain in publishedResult.
        List<String> text = new ArrayList<>();
        for (var item : snapshot.result().items()) {
            item.claims().forEach(c -> text.add(c.text()));
            item.issues().forEach(i -> { text.add(i.message()); text.add(i.nextStep()); });
            item.clarification().forEach(c -> text.add(c.question()));
        }
        var execution = new Execution(identity, String.join("\n", text),
                RuleAgentRunTrace.project(snapshot, snapshot.turnId(), events, false),
                null, false, null, false, null, false, Map.of());
        return new Bundle(execution, snapshot.result(), events);
    }

    /** Caller selects a controlled local path; never overwrites a previous execution. Contains source excerpts. */
    public static void write(Path output, Bundle bundle) throws java.io.IOException {
        var json = new ObjectMapper();
        var node = json.valueToTree(bundle);
        redact(node);
        byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static void redact(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove(List.of("originalToken", "continuationToken"));
        }
        node.forEach(RuleFactExecutionCapture::redact);
    }

    /** Offline import of host-exported typed snapshot/events; never invokes a model or database. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("identity.json snapshot.json events.json output.json required");
        var json = new ObjectMapper();
        var identity = json.readValue(Files.readAllBytes(Path.of(args[0])), Identity.class);
        var snapshot = json.readValue(Files.readAllBytes(Path.of(args[1])), TaskSnapshot.class);
        var events = json.readValue(Files.readAllBytes(Path.of(args[2])), RuleAgentRunner.Event[].class);
        write(Path.of(args[3]), capture(identity, snapshot, List.of(events)));
    }
}
