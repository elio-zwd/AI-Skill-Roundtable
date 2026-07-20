# Gemini 2.5 Flash Specifications and Usage Guide

Gemini 2.5 Flash is a high-performance, cost-efficient, and low-latency "workhorse" model designed for high-frequency, large-scale agentic workflows and multimodal applications. Released in June 2025, it serves as an excellent balance between speed and advanced reasoning capabilities.

---

## 1. Core Specifications

| Metric / Parameter | Specification | Note / Detail |
|---|---|---|
| **Context Window** | 1,048,576 tokens | 1M input token buffer |
| **Output Token Limit** | 65,536 tokens | Maximum generated response length |
| **Input Modalities** | Text, Image, Audio, Video | Native multimodal understanding |
| **Knowledge Cutoff** | January 2025 | Static knowledge boundary |
| **Lifecycle Status** | Deprecated | Scheduled cutover on **October 16, 2026** |
| **Pricing (Pay-as-you-go)** | $0.30 / 1M input, $2.50 / 1M output | Free tier available with rate limits |

---

## 2. Capabilities & Tooling

Gemini 2.5 Flash natively integrates with the core developer features of the Gemini API:

*   **Google Search Grounding**: Connecting the model to live web content to synthesize real-time search results and provide inline source citations.
*   **Code Execution**: Enabling the model to write, compile, and run Python code in a secure sandbox to solve mathematical or logical queries.
*   **Function Calling**: Describing custom tools and APIs for the model to generate structured JSON requests.
*   **Structured Outputs**: Enforcing schema adherence (e.g., JSON Schema) to return data in precise programmatic shapes.
*   **Supervised Fine-Tuning (SFT)**: Allowing customization on developer datasets.

---

## 3. Interactive Call Examples (Interactions API)

For text and search tasks, configure the REST request body with `gemini-2.5-flash`.

### 3.1 Basic Content Generation

```http
POST https://generativelanguage.googleapis.com/v1beta/interactions
Content-Type: application/json
x-goog-api-key: YOUR_GEMINI_API_KEY
Api-Revision: 2026-05-20

{
  "model": "gemini-2.5-flash",
  "input": "Summarize the history of quantum computing."
}
```

### 3.2 Grounding with Google Search

In the Interactions API, enable web grounding by configuring the `tools` array with `type: "google_search"`.

```http
POST https://generativelanguage.googleapis.com/v1beta/interactions
Content-Type: application/json
x-goog-api-key: YOUR_GEMINI_API_KEY
Api-Revision: 2026-05-20

{
  "model": "gemini-2.5-flash",
  "input": "What are the latest developments in LLMs in 2026?",
  "tools": [
    {
      "type": "google_search"
    }
  ]
}
```

---

## 4. Migration to Gemini 3.5 Flash

Since Gemini 2.5 Flash has a scheduled deprecation date of **October 16, 2026**, developers are encouraged to upgrade their model strings to `gemini-3.5-flash`. 

*   **Drop-in Replacement**: For most text-generation and search-grounding workloads, simply swap `"model": "gemini-2.5-flash"` with `"model": "gemini-3.5-flash"`.
*   **Benefits of Upgrading**: Better reasoning capability, faster token generation, and improved instruction-following behavior.
