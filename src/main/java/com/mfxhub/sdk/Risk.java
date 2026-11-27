package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.mfxhub.sdk.models.RiskAccount;
import com.mfxhub.sdk.models.RiskAccountPage;
import com.mfxhub.sdk.models.RiskActivationResult;
import com.mfxhub.sdk.models.RiskActivity;
import com.mfxhub.sdk.models.RiskActivityPage;
import com.mfxhub.sdk.models.RiskChallenge;
import com.mfxhub.sdk.models.RiskChallengeAccount;
import com.mfxhub.sdk.models.RiskChallengeAccountDetail;
import com.mfxhub.sdk.models.RiskChallengeAccountPage;
import com.mfxhub.sdk.models.RiskChallengeRules;
import com.mfxhub.sdk.models.RiskCheckResult;
import com.mfxhub.sdk.models.RiskClear;
import com.mfxhub.sdk.models.RiskCloseScope;
import com.mfxhub.sdk.models.RiskCoverageSummary;
import com.mfxhub.sdk.models.RiskEquitySeries;
import com.mfxhub.sdk.models.RiskPlan;
import com.mfxhub.sdk.models.RiskPlanCreate;
import com.mfxhub.sdk.models.RiskPlanPage;
import com.mfxhub.sdk.models.RiskPolicy;
import com.mfxhub.sdk.models.RiskPolicyVersion;
import com.mfxhub.sdk.models.RiskPreview;
import com.mfxhub.sdk.models.RiskPreviewRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Risk Management ({@code client.risk()}): policies and their versions, the accounts they protect and
 * their figures, the planner, exit plans, challenges and the activity. {@code risk:read} to read,
 * {@code risk:write} to change, {@code risk:override} to relax protection or clear a breach; every route
 * needs Risk Management in the plan.
 *
 * <p>Rules, resets, orders, steps and requests are given in the API's own names, as a {@code Map} (or the
 * generated model where a method takes one). A figure in them is a {@link java.math.BigDecimal},
 * written as exact text, or a string; a {@code double} anywhere is refused before anything is sent.
 * Every write takes the caller's idempotency key and is never retried.
 */
public final class Risk {
    /** The positions a {@code close_positions} action would close. */
    public enum CloseScope implements LowerWire {
        /** Only those MFX Hub placed. */
        MFX_POSITIONS,
        /** Every position. */
        ALL_POSITIONS
    }

    private final MfxClient client;

    Risk(MfxClient client) {
        this.client = client;
    }

    private static String id(UUID value, String name) {
        return Objects.requireNonNull(value, name).toString();
    }

    private static List<String> ids(List<UUID> values, String name) {
        return Objects.requireNonNull(values, name).stream().map(v -> id(v, name)).toList();
    }

    private <T> CompletableFuture<T> post(String path, Body body, String idempotencyKey, Class<T> type) {
        return client.write("POST", path, Wire.of(body.build()), idempotencyKey, type);
    }

    /** A read sent as a POST (the planner): no key, and retried after a 429 as every read is. */
    private <T> CompletableFuture<T> ask(String path, Object body, Class<T> type) {
        return client.read(MfxRequest.write("POST", path, List.of(), Wire.of(body), null))
                .thenApply(answer -> MfxClient.body(answer, type, "POST " + path));
    }

    private static <T, P> Iterable<T> walk(Function<@Nullable String, CompletableFuture<P>> page, Function<P, Page<T>> asPage) {
        return Walk.of(cursor -> page.apply(cursor).thenApply(asPage));
    }

    // --- policies --------------------------------------------------------------------------------

    /**
     * A new policy at version 1. It protects nothing until it is activated on accounts.
     *
     * @param name the policy's name
     * @param rules its rules, in the API's names
     * @param reset when its periods reset, in the API's names
     * @param idempotencyKey the caller's key
     * @return the policy
     * @throws IllegalArgumentException a figure is a double
     */
    public CompletableFuture<RiskPolicy> createPolicy(String name, Map<String, ?> rules, Map<String, ?> reset, String idempotencyKey) {
        Body body = Body.of().put("name", Objects.requireNonNull(name, "name")).put("rules", Objects.requireNonNull(rules, "rules"))
                .put("reset", Objects.requireNonNull(reset, "reset"));
        return post("/risk/policies", body, idempotencyKey, RiskPolicy.class);
    }

    /**
     * A new version of the policy that does not relax its protection.
     *
     * @param policyId the policy
     * @param rules the version's rules
     * @param reset its reset
     * @param idempotencyKey the caller's key
     * @return the version
     */
    public CompletableFuture<RiskPolicyVersion> addPolicyVersion(UUID policyId, Map<String, ?> rules, Map<String, ?> reset,
            String idempotencyKey) {
        return newVersion(policyId, rules, reset, null, idempotencyKey);
    }

    /**
     * A new version of the policy. One that relaxes protection needs {@code risk:override} and a reason (403
     * {@code risk_override_required} otherwise).
     *
     * @param policyId the policy
     * @param rules the version's rules
     * @param reset its reset
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the version
     */
    public CompletableFuture<RiskPolicyVersion> addPolicyVersion(UUID policyId, Map<String, ?> rules, Map<String, ?> reset, String reason,
            String idempotencyKey) {
        return newVersion(policyId, rules, reset, Objects.requireNonNull(reason, "reason"), idempotencyKey);
    }

    private CompletableFuture<RiskPolicyVersion> newVersion(UUID policyId, Map<String, ?> rules, Map<String, ?> reset,
            @Nullable String reason, String idempotencyKey) {
        Body body = Body.of().put("rules", Objects.requireNonNull(rules, "rules")).put("reset", Objects.requireNonNull(reset, "reset"))
                .put("reason", reason);
        return post("/risk/policies/" + id(policyId, "policyId") + "/versions", body, idempotencyKey, RiskPolicyVersion.class);
    }

    /**
     * Archives the policy: it can no longer be activated or given a new version. Refused 409
     * {@code state_error} while any account holds it, active or paused ({@code details.accounts} names
     * them); archiving it again answers it unchanged.
     *
     * @param policyId the policy
     * @param idempotencyKey the caller's key
     * @return the policy, archived
     */
    public CompletableFuture<RiskPolicy> archivePolicy(UUID policyId, String idempotencyKey) {
        return client.write("POST", "/risk/policies/" + id(policyId, "policyId") + "/archive", null, idempotencyKey, RiskPolicy.class);
    }

    /**
     * Puts a version in force on accounts; each account's outcome separately.
     *
     * @param policyId the policy
     * @param accountIds the accounts
     * @param version the version
     * @param idempotencyKey the caller's key
     * @return each account's outcome
     */
    public CompletableFuture<RiskActivationResult> activate(UUID policyId, List<UUID> accountIds, int version, String idempotencyKey) {
        return activation(policyId, accountIds, version, null, idempotencyKey);
    }

    /**
     * Puts a version in force on accounts, with a reason; each account's outcome separately.
     *
     * @param policyId the policy
     * @param accountIds the accounts
     * @param version the version
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return each account's outcome
     */
    public CompletableFuture<RiskActivationResult> activate(UUID policyId, List<UUID> accountIds, int version, String reason,
            String idempotencyKey) {
        return activation(policyId, accountIds, version, Objects.requireNonNull(reason, "reason"), idempotencyKey);
    }

    private CompletableFuture<RiskActivationResult> activation(UUID policyId, List<UUID> accountIds, int version, @Nullable String reason,
            String idempotencyKey) {
        Body body = Body.of().put("account_ids", ids(accountIds, "accountIds")).put("version", version).put("reason", reason);
        return post("/risk/policies/" + id(policyId, "policyId") + "/activate", body, idempotencyKey, RiskActivationResult.class);
    }

    /**
     * A policy, its current version and the accounts it is on.
     *
     * @param policyId the policy
     * @return the policy
     */
    public CompletableFuture<RiskPolicy> policy(UUID policyId) {
        return client.get("/risk/policies/" + id(policyId, "policyId"), RiskPolicy.class);
    }

    /**
     * One version of a policy, with what changed and who made it.
     *
     * @param policyId the policy
     * @param version the version
     * @return the version
     */
    public CompletableFuture<RiskPolicyVersion> policyVersion(UUID policyId, int version) {
        return client.get("/risk/policies/" + id(policyId, "policyId") + "/versions/" + version, RiskPolicyVersion.class);
    }

    /**
     * Every policy, newest first unless sorted otherwise, following the cursor. Iterating blocks on each page.
     *
     * @param filter the state, the order and the page size
     * @return every policy
     */
    public Iterable<RiskPolicy> policies(ListRiskPolicies filter) {
        Objects.requireNonNull(filter, "filter");
        return Walk.of(cursor -> client.page("/risk/policies", filter.query(cursor), RiskPolicy.class));
    }

    /**
     * Every policy, newest first.
     *
     * @return every policy
     */
    public Iterable<RiskPolicy> policies() {
        return policies(ListRiskPolicies.all());
    }

    /**
     * Every version of a policy, newest first. Iterating blocks on each page.
     *
     * @param policyId the policy
     * @param limit the page size; the platform's default when null
     * @return every version
     */
    public Iterable<RiskPolicyVersion> policyVersions(UUID policyId, @Nullable Integer limit) {
        String path = "/risk/policies/" + id(policyId, "policyId") + "/versions";
        return Walk.of(cursor -> client.page(path, Query.page(cursor, limit).build(), RiskPolicyVersion.class));
    }

    /**
     * Every version of a policy, newest first.
     *
     * @param policyId the policy
     * @return every version
     */
    public Iterable<RiskPolicyVersion> policyVersions(UUID policyId) {
        return policyVersions(policyId, null);
    }

    // --- accounts --------------------------------------------------------------------------------

    /**
     * One page of accounts with their figures, and {@code stats} over the whole set.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the coverage, restriction, order and page size
     * @return the page, with its stats
     */
    public CompletableFuture<RiskAccountPage> accountsPage(@Nullable String cursor, ListRiskAccounts filter) {
        return client.get("/risk/accounts", Objects.requireNonNull(filter, "filter").query(cursor), RiskAccountPage.class);
    }

    /**
     * The first page of accounts, by daily loss used.
     *
     * @return the page, with its stats
     */
    public CompletableFuture<RiskAccountPage> accountsPage() {
        return accountsPage(null, ListRiskAccounts.all());
    }

    /**
     * Every account, in the order the filter names, following the cursor to the end. Iterating blocks.
     *
     * @param filter the coverage, restriction and order
     * @return every account
     */
    public Iterable<RiskAccount> accounts(ListRiskAccounts filter) {
        Objects.requireNonNull(filter, "filter");
        return walk(cursor -> accountsPage(cursor, filter),
                p -> new Page<>(p.getItems() == null ? List.of() : p.getItems(), p.getNextCursor(), p.getReturned(), null));
    }

    /**
     * Every account, by daily loss used.
     *
     * @return every account
     */
    public Iterable<RiskAccount> accounts() {
        return accounts(ListRiskAccounts.all());
    }

    /**
     * One account's figures, each with its inputs and their times.
     *
     * @param accountId the account
     * @return the account
     */
    public CompletableFuture<RiskAccount> account(UUID accountId) {
        return client.get("/risk/accounts/" + id(accountId, "accountId"), RiskAccount.class);
    }

    /**
     * The account's equity and balance from its stored reads, from a moment to now.
     *
     * @param accountId the account
     * @param start the first moment
     * @return the series
     */
    public CompletableFuture<RiskEquitySeries> equity(UUID accountId, OffsetDateTime start) {
        return equity(accountId, start, null);
    }

    /**
     * The account's equity and balance from its stored reads, between two moments.
     *
     * @param accountId the account
     * @param start the first moment
     * @param end the last; now when null
     * @return the series
     */
    public CompletableFuture<RiskEquitySeries> equity(UUID accountId, OffsetDateTime start, @Nullable OffsetDateTime end) {
        Query query = Query.of().add("from", Objects.requireNonNull(start, "start")).add("to", end);
        return client.get("/risk/accounts/" + id(accountId, "accountId") + "/equity", query.build(), RiskEquitySeries.class);
    }

    /**
     * The positions a {@code close_positions} action would close now under {@code scope}.
     *
     * @param accountId the account
     * @param scope only MFX Hub's positions, or every one
     * @return the positions
     */
    public CompletableFuture<RiskCloseScope> closeScope(UUID accountId, CloseScope scope) {
        return client.get("/risk/accounts/" + id(accountId, "accountId") + "/close-scope",
                Query.of().add("scope", Objects.requireNonNull(scope, "scope")).build(), RiskCloseScope.class);
    }

    /**
     * Ends the account's activation ({@code risk:override}): no rule applies after it.
     *
     * @param accountId the account
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the platform's answer, as it wrote it
     */
    public CompletableFuture<JsonNode> deactivate(UUID accountId, String reason, String idempotencyKey) {
        return post("/risk/accounts/" + id(accountId, "accountId") + "/deactivate", Body.of().put("reason", Objects.requireNonNull(reason, "reason")),
                idempotencyKey, JsonNode.class);
    }

    /**
     * Clears a breach, or lifts a restriction before it clears itself ({@code risk:override}).
     *
     * @param accountId the account
     * @param rule {@code daily_loss}, {@code drawdown} or {@code restriction}
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the platform's answer, as it wrote it
     */
    public CompletableFuture<JsonNode> clear(UUID accountId, RiskClear.RuleEnum rule, String reason, String idempotencyKey) {
        Body body = Body.of().put("rule", Objects.requireNonNull(rule, "rule")).put("reason", Objects.requireNonNull(reason, "reason"));
        return post("/risk/accounts/" + id(accountId, "accountId") + "/clear", body, idempotencyKey, JsonNode.class);
    }

    /**
     * Coverage by kind and state, and every account where it is not healthy.
     *
     * @return the summary
     */
    public CompletableFuture<RiskCoverageSummary> coverage() {
        return client.get("/risk/coverage", RiskCoverageSummary.class);
    }

    // --- the planner ------------------------------------------------------------------------------

    /**
     * Sizes a trade from a risk budget and checks every rule. Nothing is sent to the broker.
     *
     * @param request the request, in the API's names
     * @return the preview
     * @throws IllegalArgumentException a figure is a double
     */
    public CompletableFuture<RiskPreview> preview(Map<String, ?> request) {
        return ask("/risk/planner/preview", Objects.requireNonNull(request, "request"), RiskPreview.class);
    }

    /**
     * Sizes a trade from a risk budget and checks every rule, the request as the generated model.
     *
     * @param request the request
     * @return the preview
     */
    public CompletableFuture<RiskPreview> preview(RiskPreviewRequest request) {
        return ask("/risk/planner/preview", Objects.requireNonNull(request, "request"), RiskPreview.class);
    }

    /**
     * Checks an order already sized, exactly as the pre-trade check would. Nothing is sent to the broker.
     *
     * @param accountId the account
     * @param order the order, in the API's names: {@code symbol}, {@code side}, {@code type}, {@code volume}
     *     and the rest
     * @return the verdict and every rule's check
     * @throws IllegalArgumentException a figure is a double
     */
    public CompletableFuture<RiskCheckResult> check(UUID accountId, Map<String, ?> order) {
        Body body = Body.of().put("account_id", id(accountId, "accountId")).put("order", Objects.requireNonNull(order, "order"));
        return ask("/risk/planner/check", body.build(), RiskCheckResult.class);
    }

    // --- exit plans -------------------------------------------------------------------------------

    /**
     * An exit plan on a held position ({@code ticket}) or placed with its order ({@code order}). An entry a
     * rule refuses is a {@link RiskRefusedException} naming its rule.
     *
     * @param plan the plan, in the API's names
     * @param idempotencyKey the caller's key
     * @return the plan
     * @throws IllegalArgumentException a figure is a double
     */
    public CompletableFuture<RiskPlan> createPlan(Map<String, ?> plan, String idempotencyKey) {
        return client.write("POST", "/risk/plans", Wire.of(Objects.requireNonNull(plan, "plan")), idempotencyKey, RiskPlan.class);
    }

    /**
     * An exit plan, as the generated model.
     *
     * @param plan the plan
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> createPlan(RiskPlanCreate plan, String idempotencyKey) {
        return client.write("POST", "/risk/plans", Objects.requireNonNull(plan, "plan"), idempotencyKey, RiskPlan.class);
    }

    /**
     * An exit plan, each step with its holder, state and outcome, and {@code r_now}.
     *
     * @param planId the plan
     * @return the plan
     */
    public CompletableFuture<RiskPlan> plan(UUID planId) {
        return client.get("/risk/plans/" + id(planId, "planId"), RiskPlan.class);
    }

    /**
     * One page of plans, with {@code stats} over the filtered set.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the accounts, states, order and page size
     * @return the page, with its stats
     */
    public CompletableFuture<RiskPlanPage> plansPage(@Nullable String cursor, ListRiskPlans filter) {
        return client.get("/risk/plans", Objects.requireNonNull(filter, "filter").query(cursor), RiskPlanPage.class);
    }

    /**
     * The first page of plans, newest first.
     *
     * @return the page
     */
    public CompletableFuture<RiskPlanPage> plansPage() {
        return plansPage(null, ListRiskPlans.all());
    }

    /**
     * Every plan, following the cursor to the end. Iterating blocks on each page.
     *
     * @param filter the accounts, states and order
     * @return every plan
     */
    public Iterable<RiskPlan> plans(ListRiskPlans filter) {
        Objects.requireNonNull(filter, "filter");
        return walk(cursor -> plansPage(cursor, filter),
                p -> new Page<>(p.getItems() == null ? List.of() : p.getItems(), p.getNextCursor(), p.getReturned(), null));
    }

    /**
     * Every plan, newest first.
     *
     * @return every plan
     */
    public Iterable<RiskPlan> plans() {
        return plans(ListRiskPlans.all());
    }

    /**
     * A new version of the plan's steps.
     *
     * @param planId the plan
     * @param steps the steps, in the API's names
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> amendPlan(UUID planId, List<? extends Map<String, ?>> steps, String idempotencyKey) {
        return amend(planId, steps, null, idempotencyKey);
    }

    /**
     * A new version of the plan's steps, with a reason.
     *
     * @param planId the plan
     * @param steps the steps, in the API's names
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> amendPlan(UUID planId, List<? extends Map<String, ?>> steps, String reason, String idempotencyKey) {
        return amend(planId, steps, Objects.requireNonNull(reason, "reason"), idempotencyKey);
    }

    private CompletableFuture<RiskPlan> amend(UUID planId, List<? extends Map<String, ?>> steps, @Nullable String reason, String idempotencyKey) {
        Body body = Body.of().put("steps", List.copyOf(Objects.requireNonNull(steps, "steps"))).put("reason", reason);
        return post("/risk/plans/" + id(planId, "planId") + "/amend", body, idempotencyKey, RiskPlan.class);
    }

    /**
     * Pauses the plan's MFX-held steps; stops held at the broker stay where they are.
     *
     * @param planId the plan
     * @param reason why, for the audit
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> pausePlan(UUID planId, String reason, String idempotencyKey) {
        return post("/risk/plans/" + id(planId, "planId") + "/pause", Body.of().put("reason", Objects.requireNonNull(reason, "reason")),
                idempotencyKey, RiskPlan.class);
    }

    /**
     * Resumes the plan's MFX-held steps.
     *
     * @param planId the plan
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> resumePlan(UUID planId, String idempotencyKey) {
        return client.write("POST", "/risk/plans/" + id(planId, "planId") + "/resume", null, idempotencyKey, RiskPlan.class);
    }

    /**
     * Ends the plan; stops held at the broker stay where they are.
     *
     * @param planId the plan
     * @param idempotencyKey the caller's key
     * @return the plan
     */
    public CompletableFuture<RiskPlan> endPlan(UUID planId, String idempotencyKey) {
        return client.write("DELETE", "/risk/plans/" + id(planId, "planId"), null, idempotencyKey, RiskPlan.class);
    }

    // --- challenges -------------------------------------------------------------------------------

    /**
     * A challenge at version 1: one rule set over many accounts.
     *
     * @param name the challenge's name
     * @param rules its rules, in the API's names
     * @param idempotencyKey the caller's key
     * @return the challenge
     */
    public CompletableFuture<RiskChallenge> createChallenge(String name, Map<String, ?> rules, String idempotencyKey) {
        return post("/risk/challenges", Body.of().put("name", Objects.requireNonNull(name, "name")).put("rules", Objects.requireNonNull(rules, "rules")),
                idempotencyKey, RiskChallenge.class);
    }

    /**
     * A challenge at version 1, its rules as the generated model.
     *
     * @param name the challenge's name
     * @param rules its rules
     * @param idempotencyKey the caller's key
     * @return the challenge
     */
    public CompletableFuture<RiskChallenge> createChallenge(String name, RiskChallengeRules rules, String idempotencyKey) {
        return post("/risk/challenges", Body.of().put("name", Objects.requireNonNull(name, "name")).put("rules", Objects.requireNonNull(rules, "rules")),
                idempotencyKey, RiskChallenge.class);
    }

    /**
     * A new version, for accounts enrolled after it; one already running keeps its own.
     *
     * @param challengeId the challenge
     * @param rules the version's rules, in the API's names
     * @param idempotencyKey the caller's key
     * @return the challenge
     */
    public CompletableFuture<RiskChallenge> addChallengeVersion(UUID challengeId, Map<String, ?> rules, String idempotencyKey) {
        return client.write("POST", "/risk/challenges/" + id(challengeId, "challengeId") + "/versions",
                Wire.of(Objects.requireNonNull(rules, "rules")), idempotencyKey, RiskChallenge.class);
    }

    /**
     * A new version, its rules as the generated model.
     *
     * @param challengeId the challenge
     * @param rules the version's rules
     * @param idempotencyKey the caller's key
     * @return the challenge
     */
    public CompletableFuture<RiskChallenge> addChallengeVersion(UUID challengeId, RiskChallengeRules rules, String idempotencyKey) {
        return client.write("POST", "/risk/challenges/" + id(challengeId, "challengeId") + "/versions", Objects.requireNonNull(rules, "rules"),
                idempotencyKey, RiskChallenge.class);
    }

    /**
     * Archives the challenge: no account can be enrolled in it or given a new version of it. Refused 409
     * {@code state_error} while any account is in progress ({@code details.accounts} names them); archiving
     * it again answers it unchanged.
     *
     * @param challengeId the challenge
     * @param idempotencyKey the caller's key
     * @return the challenge, archived
     */
    public CompletableFuture<RiskChallenge> archiveChallenge(UUID challengeId, String idempotencyKey) {
        return client.write("POST", "/risk/challenges/" + id(challengeId, "challengeId") + "/archive", null, idempotencyKey,
                RiskChallenge.class);
    }

    /**
     * A challenge, its current rule set and its counts.
     *
     * @param challengeId the challenge
     * @return the challenge
     */
    public CompletableFuture<RiskChallenge> challenge(UUID challengeId) {
        return client.get("/risk/challenges/" + id(challengeId, "challengeId"), RiskChallenge.class);
    }

    /**
     * Every challenge, newest first, each with its counts. Iterating blocks on each page.
     *
     * @param limit the page size; the platform's default when null
     * @return every challenge
     */
    public Iterable<RiskChallenge> challenges(@Nullable Integer limit) {
        return Walk.of(cursor -> client.page("/risk/challenges", Query.page(cursor, limit).build(), RiskChallenge.class));
    }

    /**
     * Every challenge, newest first.
     *
     * @return every challenge
     */
    public Iterable<RiskChallenge> challenges() {
        return challenges(null);
    }

    /**
     * Enrols accounts in the challenge's current version; each account's outcome separately (one with a
     * stale read is refused {@code risk_inputs_stale}).
     *
     * @param challengeId the challenge
     * @param accountIds the accounts
     * @param idempotencyKey the caller's key
     * @return the platform's answer, as it wrote it
     */
    public CompletableFuture<JsonNode> enrol(UUID challengeId, List<UUID> accountIds, String idempotencyKey) {
        return post("/risk/challenges/" + id(challengeId, "challengeId") + "/accounts", Body.of().put("account_ids", ids(accountIds, "accountIds")),
                idempotencyKey, JsonNode.class);
    }

    /**
     * One page of the challenge's accounts, with {@code stats} over the filtered set. This list, paged, is
     * the export.
     *
     * @param challengeId the challenge
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the states, rules, minimum days, order and page size
     * @return the page, with its stats
     */
    public CompletableFuture<RiskChallengeAccountPage> challengeAccountsPage(UUID challengeId, @Nullable String cursor,
            ListChallengeAccounts filter) {
        return client.get("/risk/challenges/" + id(challengeId, "challengeId") + "/accounts", Objects.requireNonNull(filter, "filter").query(cursor),
                RiskChallengeAccountPage.class);
    }

    /**
     * Every account of the challenge, in the order the filter names. Iterating blocks on each page.
     *
     * @param challengeId the challenge
     * @param filter the states, rules, minimum days and order
     * @return every account
     */
    public Iterable<RiskChallengeAccount> challengeAccounts(UUID challengeId, ListChallengeAccounts filter) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(filter, "filter");
        return walk(cursor -> challengeAccountsPage(challengeId, cursor, filter),
                p -> new Page<>(p.getItems() == null ? List.of() : p.getItems(), p.getNextCursor(), p.getReturned(), null));
    }

    /**
     * Every account of the challenge, by daily loss used.
     *
     * @param challengeId the challenge
     * @return every account
     */
    public Iterable<RiskChallengeAccount> challengeAccounts(UUID challengeId) {
        return challengeAccounts(challengeId, ListChallengeAccounts.all());
    }

    /**
     * One enrolled account, its result and its periods with their statistics.
     *
     * @param challengeId the challenge
     * @param accountId the account
     * @return the account
     */
    public CompletableFuture<RiskChallengeAccountDetail> challengeAccount(UUID challengeId, UUID accountId) {
        return client.get("/risk/challenges/" + id(challengeId, "challengeId") + "/accounts/" + id(accountId, "accountId"),
                RiskChallengeAccountDetail.class);
    }

    // --- activity ---------------------------------------------------------------------------------

    /**
     * One page of activity, unresolved first by default, with {@code stats}.
     *
     * @param cursor the cursor of the page before; the first page when null
     * @param filter the kinds, accounts, outcomes, window, order and page size
     * @return the page, with its stats
     */
    public CompletableFuture<RiskActivityPage> activityPage(@Nullable String cursor, ListRiskActivity filter) {
        return client.get("/risk/activity", Objects.requireNonNull(filter, "filter").query(cursor), RiskActivityPage.class);
    }

    /**
     * The first page of activity, unresolved first.
     *
     * @return the page
     */
    public CompletableFuture<RiskActivityPage> activityPage() {
        return activityPage(null, ListRiskActivity.all());
    }

    /**
     * Every warning, breach, action and change, following the cursor to the end. Iterating blocks.
     *
     * @param filter the kinds, accounts, outcomes, window and order
     * @return every row of activity
     */
    public Iterable<RiskActivity> activity(ListRiskActivity filter) {
        Objects.requireNonNull(filter, "filter");
        return walk(cursor -> activityPage(cursor, filter),
                p -> new Page<>(p.getItems() == null ? List.of() : p.getItems(), p.getNextCursor(), p.getReturned(), null));
    }

    /**
     * Every row of activity, unresolved first.
     *
     * @return every row
     */
    public Iterable<RiskActivity> activity() {
        return activity(ListRiskActivity.all());
    }
}
