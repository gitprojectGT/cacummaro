import React, { useState } from 'react';
import ProcessingSteps from './ProcessingSteps';
import './DocumentIngestionForm.css';

const DocumentIngestionForm = () => {
    const [url, setUrl] = useState('');
    const [isProcessing, setIsProcessing] = useState(false);
    const [currentDocumentId, setCurrentDocumentId] = useState(null);
    const [processingComplete, setProcessingComplete] = useState(false);
    const [error, setError] = useState(null);
    const [result, setResult] = useState(null);

    const validateUrl = (url) => {
        try {
            new URL(url);
            return true;
        } catch {
            return false;
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!url.trim()) {
            setError('Please enter a URL');
            return;
        }

        if (!validateUrl(url)) {
            setError('Please enter a valid URL');
            return;
        }

        setError(null);
        setIsProcessing(true);
        setProcessingComplete(false);
        setResult(null);

        try {
            const response = await fetch('/api/v1/ingest', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    url: url,
                    options: {
                        createObsidianNote: true,
                        noteMetaTag: 'data-note'
                    }
                })
            });

            const data = await response.json();

            if (response.ok) {
                setCurrentDocumentId(data.id);
                setResult(data);
            } else {
                setError(data.message || 'Failed to process URL');
                setIsProcessing(false);
            }
        } catch (err) {
            setError('Network error. Please try again.');
            setIsProcessing(false);
        }
    };

    const handleProcessingComplete = (status) => {
        setIsProcessing(false);
        setProcessingComplete(true);
        setUrl(''); // Clear the input
    };

    const handleProcessingError = (errorMessage) => {
        setIsProcessing(false);
        setError(errorMessage);
    };

    const handleReset = () => {
        setUrl('');
        setIsProcessing(false);
        setCurrentDocumentId(null);
        setProcessingComplete(false);
        setError(null);
        setResult(null);
    };

    return (
        <div className="document-ingestion-form">
            <div className="form-header">
                <div className="form-icon">📄</div>
                <h3>Web to PDF Converter</h3>
            </div>

            {!isProcessing && !processingComplete && (
                <form onSubmit={handleSubmit} className="url-form">
                    <div className="input-group">
                        <input
                            type="url"
                            value={url}
                            onChange={(e) => setUrl(e.target.value)}
                            placeholder="Enter URL (e.g., https://example.com)"
                            className={`url-input ${error ? 'error' : ''}`}
                            disabled={isProcessing}
                        />
                        <button
                            type="submit"
                            className="submit-button"
                            disabled={isProcessing || !url.trim()}
                        >
                            {isProcessing ? 'Processing...' : 'Convert to PDF'}
                        </button>
                    </div>

                    {error && (
                        <div className="error-message">
                            <span className="error-icon">⚠️</span>
                            {error}
                        </div>
                    )}
                </form>
            )}

            {(isProcessing || processingComplete) && (
                <div className="processing-section">
                    <ProcessingSteps
                        documentId={currentDocumentId}
                        onComplete={handleProcessingComplete}
                        onError={handleProcessingError}
                    />

                    {processingComplete && result && (
                        <div className="completion-actions">
                            <a
                                href={result.pdfUrl}
                                className="download-button"
                                download
                            >
                                📄 Download PDF
                            </a>

                            <button
                                onClick={handleReset}
                                className="reset-button"
                            >
                                🔄 Process Another URL
                            </button>
                        </div>
                    )}

                    {error && (
                        <div className="error-actions">
                            <button
                                onClick={handleReset}
                                className="reset-button"
                            >
                                🔄 Try Again
                            </button>
                        </div>
                    )}
                </div>
            )}

            <div className="feature-info">
                <div className="info-item">
                    <span className="info-icon">🔍</span>
                    <span>URL verification and accessibility check</span>
                </div>
                <div className="info-item">
                    <span className="info-icon">🤖</span>
                    <span>Automatic content analysis and categorization</span>
                </div>
                <div className="info-item">
                    <span className="info-icon">💾</span>
                    <span>Secure storage with unique document ID</span>
                </div>
                <div className="info-item">
                    <span className="info-icon">📝</span>
                    <span>Optional Obsidian note generation</span>
                </div>
            </div>
        </div>
    );
};

export default DocumentIngestionForm;