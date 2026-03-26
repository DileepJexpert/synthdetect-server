import time
import re
import math
from typing import Optional
from collections import Counter

from app.core.config import settings
from app.core.logging import logger
from app.models.schemas import DetectionResult, Signal

# Lazy-loaded model globals
_text_model = None
_text_tokenizer = None
_model_version = "v1.0-mock"


def _load_model():
    global _text_model, _text_tokenizer, _model_version
    if _text_model is not None:
        return

    try:
        from transformers import AutoTokenizer, AutoModelForSequenceClassification
        import torch

        logger.info("loading_text_model", model=settings.text_model_name)
        _text_tokenizer = AutoTokenizer.from_pretrained(
            settings.text_model_name,
            cache_dir=settings.model_cache_dir,
        )
        _text_model = AutoModelForSequenceClassification.from_pretrained(
            settings.text_model_name,
            cache_dir=settings.model_cache_dir,
        )
        _text_model.eval()
        _model_version = f"transformers-{settings.text_model_name.split('/')[-1]}"
        logger.info("text_model_loaded", version=_model_version)

    except Exception as e:
        logger.warning("text_model_load_failed", error=str(e), fallback="heuristic")
        _text_model = None
        _text_tokenizer = None
        _model_version = "v1.0-heuristic"


def _compute_perplexity_proxy(text: str) -> float:
    """
    Approximates perplexity using character/word n-gram entropy.
    AI text tends to have lower perplexity (more predictable).
    Returns value 0..1 where higher = more likely AI.
    """
    words = text.lower().split()
    if len(words) < 10:
        return 0.5

    # Unigram entropy
    word_counts = Counter(words)
    total = sum(word_counts.values())
    unigram_entropy = -sum((c / total) * math.log2(c / total) for c in word_counts.values())

    # Bigram entropy
    bigrams = list(zip(words, words[1:]))
    bigram_counts = Counter(bigrams)
    bigram_total = sum(bigram_counts.values())
    bigram_entropy = -sum((c / bigram_total) * math.log2(c / bigram_total)
                          for c in bigram_counts.values())

    # Normalize: lower entropy → higher AI probability
    max_unigram = math.log2(len(word_counts))
    norm_unigram = unigram_entropy / max_unigram if max_unigram > 0 else 0.5

    ai_score = 1.0 - min(1.0, norm_unigram * 0.6 + (bigram_entropy / (max_unigram + 1)) * 0.4)
    return round(float(ai_score), 4)


def _compute_burstiness(text: str) -> float:
    """
    Human writing has more variation in sentence length (burstiness).
    AI text tends to have uniform sentence lengths.
    Returns 0..1 where higher = more likely AI (less bursty).
    """
    sentences = re.split(r'[.!?]+', text)
    lengths = [len(s.split()) for s in sentences if len(s.split()) > 2]
    if len(lengths) < 3:
        return 0.5

    mean_len = sum(lengths) / len(lengths)
    variance = sum((l - mean_len) ** 2 for l in lengths) / len(lengths)
    std_dev = math.sqrt(variance)

    # Low std deviation → uniform length → likely AI
    ai_score = 1.0 - min(1.0, std_dev / 15.0)
    return round(float(ai_score), 4)


def _compute_vocabulary_richness(text: str) -> float:
    """
    Type-Token Ratio: AI text tends to use a narrower vocabulary.
    Returns 0..1 where higher = more likely AI.
    """
    words = re.findall(r'\b[a-z]+\b', text.lower())
    if len(words) < 20:
        return 0.5

    ttr = len(set(words)) / len(words)
    # Low TTR → less vocabulary diversity → more AI-like
    ai_score = 1.0 - min(1.0, ttr * 1.5)
    return round(float(ai_score), 4)


def _compute_punctuation_pattern(text: str) -> float:
    """
    AI text tends to have more consistent and 'correct' punctuation.
    Humans often use informal punctuation patterns.
    """
    total_chars = len(text)
    if total_chars < 50:
        return 0.5

    # Count specific patterns
    ellipsis_count = text.count('...')
    exclamation_count = text.count('!')
    informal_chars = text.count('?!') + text.count('!!') + ellipsis_count

    informal_ratio = informal_chars / max(1, total_chars / 100)
    # Low informal punctuation → more AI-like
    ai_score = 1.0 - min(1.0, informal_ratio * 2.0)
    return round(float(ai_score), 4)


def _heuristic_text_analysis(text: str) -> dict:
    perplexity_score = _compute_perplexity_proxy(text)
    burstiness_score = _compute_burstiness(text)
    vocab_score = _compute_vocabulary_richness(text)
    punctuation_score = _compute_punctuation_pattern(text)

    composite = (
        perplexity_score * 0.35 +
        burstiness_score * 0.30 +
        vocab_score * 0.20 +
        punctuation_score * 0.15
    )

    return {
        "composite": composite,
        "signals": [
            Signal(name="perplexity", value=perplexity_score, weight=0.35,
                   description="Text predictability — AI text has lower perplexity"),
            Signal(name="sentence_burstiness", value=burstiness_score, weight=0.30,
                   description="Sentence length variation — human writing is less uniform"),
            Signal(name="vocabulary_richness", value=vocab_score, weight=0.20,
                   description="Type-token ratio — AI text uses narrower vocabulary"),
            Signal(name="punctuation_pattern", value=punctuation_score, weight=0.15,
                   description="Informal punctuation usage — humans use more varied punctuation"),
        ]
    }


def detect_text(text: str, language: str = "en") -> DetectionResult:
    start_ms = int(time.time() * 1000)
    _load_model()

    # Truncate for model limits
    truncated = text[:settings.text_max_tokens * 4]

    if _text_model is not None and _text_tokenizer is not None:
        import torch
        inputs = _text_tokenizer(
            truncated,
            return_tensors="pt",
            max_length=settings.text_max_tokens,
            truncation=True,
            padding=True,
        )
        with torch.no_grad():
            outputs = _text_model(**inputs)
            probs = torch.softmax(outputs.logits, dim=-1)[0]

        # Label 1 = "Fake" / AI-generated for roberta-openai-detector
        confidence = round(float(probs[1]), 4) if len(probs) > 1 else round(float(probs[0]), 4)
        signals = [
            Signal(name="model_confidence", value=confidence, weight=1.0,
                   description=f"RoBERTa classifier output ({_model_version})"),
        ]
    else:
        result = _heuristic_text_analysis(text)
        confidence = round(float(result["composite"]), 4)
        signals = result["signals"]

    is_synthetic = confidence >= settings.confidence_threshold_synthetic
    elapsed = int(time.time() * 1000) - start_ms

    logger.info("text_detection_complete",
                language=language,
                text_len=len(text),
                confidence=confidence,
                is_synthetic=is_synthetic,
                elapsed_ms=elapsed)

    return DetectionResult(
        isSynthetic=is_synthetic,
        confidenceScore=confidence,
        modelVersion=_model_version,
        processingMs=elapsed,
        signals=signals,
    )
