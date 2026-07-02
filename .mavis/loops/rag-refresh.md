# RAG Refresh Loop

## Goal

Synchronize versioned project memory and RAG metadata with verified code.

## Rules

- Code wins over RAG.
- Do not index secrets.
- Do not index raw VIN/GPS logs.
- Do not index PII.
- Store summaries and metadata in Git.