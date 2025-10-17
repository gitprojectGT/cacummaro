import React, { useState, useEffect } from 'react';
import './ProcessingSteps.css';

const ProcessingSteps = ({ documentId, onComplete, onError }) => {
    const [currentStep, setCurrentStep] = useState(0);
    const [processingStatus, setProcessingStatus] = useState(null);
    const [isProcessing, setIsProcessing] = useState(false);

    const steps = [
        {
            id: 'URL_VERIFICATION',
            icon: '🔍',
            title: 'Verifying URL accessibility',
            description: 'Checking if the URL is reachable and accessible'
        },
        {
            id: 'PDF_CONVERSION',
            icon: '📄',
            title: 'Converting web page to PDF',
            description: 'Using headless browser to render and convert page'
        },
        {
            id: 'STORAGE',
            icon: '💾',
            title: 'Storing in CouchDB with UUID',
            description: 'Saving document with unique identifier in database'
        },
        {
            id: 'CONTENT_ANALYSIS',
            icon: '🤖',
            title: 'Analyzing PDF content',
            description: 'Extracting metadata and analyzing document content'
        },
        {
            id: 'CATEGORIZATION',
            icon: '🏷️',
            title: 'Categorizing document',
            description: 'Applying AI classification to determine category'
        }
    ];

    const categories = [
        'Technology',
        'Newspaper Article',
        'Technology Update',
        'Simple Article'
    ];

    useEffect(() => {
        if (documentId && isProcessing) {
            const interval = setInterval(async () => {
                try {
                    const response = await fetch(`/api/v1/documents/${documentId}/status`);
                    if (response.ok) {
                        const status = await response.json();
                        setProcessingStatus(status);

                        // Update current step based on status
                        const stepIndex = steps.findIndex(step =>
                            step.id === status.currentStep
                        );
                        if (stepIndex !== -1) {
                            setCurrentStep(stepIndex);
                        }

                        // Check if processing is complete
                        if (status.completed || status.failed) {
                            setIsProcessing(false);
                            clearInterval(interval);

                            if (status.completed) {
                                onComplete(status);
                            } else if (status.failed) {
                                onError(status.errorMessage);
                            }
                        }
                    }
                } catch (error) {
                    console.error('Error fetching processing status:', error);
                }
            }, 1000);

            return () => clearInterval(interval);
        }
    }, [documentId, isProcessing]);

    const startProcessing = () => {
        setIsProcessing(true);
        setCurrentStep(0);
        setProcessingStatus(null);
    };

    const getStepStatus = (stepIndex) => {
        if (stepIndex < currentStep) return 'completed';
        if (stepIndex === currentStep) return 'active';
        return 'pending';
    };

    return (
        <div className="processing-steps">
            {!isProcessing && !processingStatus && (
                <div className="processing-start">
                    <h3>Document Processing Pipeline</h3>
                    <p>Your document will go through these steps:</p>
                </div>
            )}

            <div className="steps-container">
                {steps.map((step, index) => {
                    const status = getStepStatus(index);
                    const completedStep = processingStatus?.completedSteps?.find(
                        cs => cs.step === step.id
                    );

                    return (
                        <div key={step.id} className={`step step-${status}`}>
                            <div className="step-indicator">
                                <div className="step-icon">
                                    {status === 'completed' ? '✅' :
                                     status === 'active' ? (
                                        <div className="spinner">{step.icon}</div>
                                     ) : step.icon}
                                </div>
                                <div className="step-number">{index + 1}</div>
                            </div>

                            <div className="step-content">
                                <h4 className="step-title">{step.title}</h4>
                                <p className="step-description">{step.description}</p>

                                {completedStep && (
                                    <div className="step-result">
                                        <span className={`result-indicator ${
                                            completedStep.success ? 'success' : 'error'
                                        }`}>
                                            {completedStep.success ? '✓' : '✗'}
                                        </span>
                                        <span className="result-message">
                                            {completedStep.message}
                                        </span>
                                    </div>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {processingStatus?.completed && (
                <div className="processing-complete">
                    <div className="completion-header">
                        <span className="completion-icon">🎉</span>
                        <h3>Processing Complete!</h3>
                    </div>

                    <div className="completion-details">
                        <div className="detail-item">
                            <strong>Document ID:</strong>
                            <code>{processingStatus.documentId}</code>
                        </div>

                        <div className="detail-item">
                            <strong>Processing Time:</strong>
                            <span>{formatDuration(processingStatus.startedAt, processingStatus.completedAt)}</span>
                        </div>

                        <div className="detail-item">
                            <strong>Categories Applied:</strong>
                            <div className="category-tags">
                                {categories.map(category => (
                                    <span key={category} className="category-tag">
                                        {category}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {processingStatus?.failed && (
                <div className="processing-failed">
                    <div className="failure-header">
                        <span className="failure-icon">❌</span>
                        <h3>Processing Failed</h3>
                    </div>
                    <p className="failure-message">{processingStatus.errorMessage}</p>
                </div>
            )}
        </div>
    );
};

const formatDuration = (startTime, endTime) => {
    const start = new Date(startTime);
    const end = new Date(endTime);
    const durationMs = end - start;
    const seconds = Math.round(durationMs / 1000);
    return `${seconds}s`;
};

export default ProcessingSteps;