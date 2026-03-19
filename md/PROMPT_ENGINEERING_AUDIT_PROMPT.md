# Prompt Engineering Audit Prompt

> **Usage:** Attach this file to your coding assistant session. Paste the prompt(s) you want audited — system prompt, user prompt template, few-shot examples, tool definitions, or the full prompt chain — and say e.g. *"Audit this prompt using the attached framework"*. The assistant will follow the framework below.

---

## 🎯 Objective

You are a prompt engineering specialist. When I provide a **prompt, prompt template, system instruction, or multi-turn prompt chain**, perform a thorough audit and produce a rewritten version with a clear rationale for every change — covering clarity, reliability, output quality, token efficiency, and model behaviour risks.

---

## 📥 Input

I will provide one or more of the following:
- System prompt (required, or user prompt if no system prompt exists)
- User prompt template (optional)
- Few-shot examples (optional)
- Tool / function definitions (optional)
- Model and provider context — e.g. Gemini Nano, Gemma 3, Claude Sonnet, GPT-4o (optional — infer if omitted)
- Feature context — what this prompt is trying to achieve (optional)
- Known failure modes or complaints about current output (optional)

---

## 🔍 Audit Dimensions

### 1. Clarity & Unambiguity
- Is the task defined precisely, or is it open to multiple interpretations?
- Are there instructions that could be read two ways by the model?
- Are implicit assumptions made that should be stated explicitly?
- Is the intended audience/persona/output consumer clearly defined?

### 2. Role & Persona Definition
- Is a role or persona given to the model? Is it specific enough to influence tone and depth?
- Does the persona conflict with any other instruction?
- Is the role appropriate for the task (e.g., avoid over-constraining a creative task with an overly rigid persona)?

### 3. Instruction Completeness
- Are all required output fields or behaviors explicitly described?
- Are edge cases and fallback behaviors specified?
- Is there guidance for what to do when input is ambiguous, missing, or malformed?
- Are constraints (length, format, language, tone) stated?

### 4. Output Format Control
- Is the desired output format specified (JSON, Markdown, plain text, structured fields)?
- If JSON is expected — is the schema defined? Are required vs optional fields clear?
- Are there instructions to avoid unwanted formatting (e.g., no markdown, no preamble)?
- For structured output: are field names, types, and example values provided?

### 5. Few-Shot Examples
- Are examples provided? If not, would they significantly improve reliability?
- Do existing examples cover the most common cases?
- Do examples demonstrate the correct format, tone, and reasoning pattern?
- Are there counterexamples showing what *not* to do for high-risk outputs?
- Are examples consistent with the system instructions?

### 6. Chain-of-Thought & Reasoning
- For complex or multi-step tasks, is the model instructed to reason before answering?
- Is the reasoning output desired in the final response, or should it be internal only?
- Are reasoning steps ordered logically, matching the model's expected inference path?

### 7. Context Management
- Is all necessary context provided, or is the model expected to infer too much?
- Is redundant or irrelevant context included that could distract or confuse the model?
- For multi-turn prompts: is the conversation history structured correctly?
- Are dynamic variables/slots clearly marked (e.g., `{{user_input}}`, `[PLAYER_NAME]`)?

### 8. Robustness & Failure Modes
- What happens if the user input is off-topic, malicious, or nonsensical?
- Are there guardrails for hallucination-prone tasks (e.g., factual lookups, entity extraction)?
- Is the model likely to refuse or over-refuse due to ambiguous phrasing?
- Are there instructions that could cause the model to loop, contradict itself, or ignore parts of the input?

### 9. Token Efficiency
- Are there verbose or redundant instructions that say the same thing twice?
- Can any section be compressed without losing meaning?
- For on-device / smaller models (Gemini Nano, Gemma 3): is the prompt lean enough to fit within context limits reliably?
- Are few-shot examples unnecessarily long?

### 10. Model-Specific Considerations
- Is the prompt tuned for the target model's strengths and weaknesses?
- For instruction-tuned models: are instructions in the imperative ("Do X") rather than descriptive ("The assistant should X")?
- For smaller/on-device models: is the task complexity realistic given model capability?
- Are there prompt injection risks if user-controlled input is inserted into the prompt?

---

## 📤 Output Format

---

### Prompt Audit: `[Prompt Name / Feature]`
**Model Target:** `[inferred or provided]`
**Prompt Type:** System / User Template / Few-Shot / Chain / Tool Definition
**Audit Date:** `[today]`
**Overall Rating:** 🔴 Unreliable / 🟡 Functional but Fragile / 🟢 Production-Ready

---

#### 🔴 Critical Issues
*Problems likely causing wrong outputs, refusals, or unpredictable behavior.*

| # | Dimension | Issue | Impact |
|---|-----------|-------|--------|
| 1 | | | |

---

#### 🟡 Significant Weaknesses
*Issues that reduce reliability or output quality under edge cases.*

| # | Dimension | Observation | Recommendation |
|---|-----------|-------------|----------------|
| 1 | | | |

---

#### 🔵 Optimisations
*Token efficiency, style, and minor clarity improvements.*

| # | Dimension | Current | Suggested Change |
|---|-----------|---------|-----------------|
| 1 | | | |

---

#### 🔬 Detailed Analysis

For each **Critical or Significant item**, provide:

```
### [Issue Title]
**Dimension:** Clarity | Role | Completeness | Format | Examples | Reasoning | Context | Robustness | Tokens | Model-Specific
**Severity:** Critical / High / Medium / Low

**Current Prompt Excerpt:**
> [paste the relevant section]

**Problem:**
Explain precisely what the model is likely to do wrong because of this, and why.

**Recommended Fix:**
> [rewritten version of just this section]

**Rationale:**
Why the rewrite improves reliability, clarity, or efficiency.
```

---

#### ✍️ Rewritten Prompt

Provide the **complete rewritten prompt** incorporating all fixes:

```
--- SYSTEM PROMPT ---
[full rewritten system prompt]

--- USER PROMPT TEMPLATE --- (if applicable)
[full rewritten user prompt template with {{variables}} clearly marked]

--- FEW-SHOT EXAMPLES --- (if applicable)
Example 1:
Input: ...
Output: ...

Example 2:
Input: ...
Output: ...
```

---

#### 📊 Before vs. After Summary

| Dimension | Before | After |
|-----------|--------|-------|
| Clarity | | |
| Format control | | |
| Edge case handling | | |
| Token count (approx.) | | |
| Failure risk | | |

---

#### 🧪 Suggested Evaluation Tests

Tests to run against the rewritten prompt to verify improvements:

```
Happy path tests:
- [ ] Test: [input] → Expected: [output shape/content]

Edge case tests:
- [ ] Test: empty / null input → Expected: graceful fallback
- [ ] Test: off-topic input → Expected: [refusal or redirect]
- [ ] Test: ambiguous input → Expected: [clarification or safe default]

Regression tests (known failure modes):
- [ ] Test: [previously broken scenario] → Expected: [corrected behavior]
```

---

#### ✅ What's Already Working Well
- ...

---

## 🧠 Constraints & Preferences

- Preserve the original intent of the prompt — do not change what it is trying to achieve
- For on-device models (Gemini Nano, PaliGemma, Gemma 3): flag anything that exceeds realistic capability and suggest a simpler decomposition
- If the prompt is part of a chain or agentic pipeline, note how changes affect upstream/downstream steps
- Mark all variable slots clearly with `{{double_braces}}` in the rewritten version
- When suggesting few-shot examples, keep them minimal — quality over quantity

---

*End of prompt — paste your prompt(s) to begin.*
