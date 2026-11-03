package com.example.aipractice.exception;

/** An unknown or already consumed proposal cannot be approved or rejected. */
public class ProposalNotPendingException extends IllegalStateException {

    public ProposalNotPendingException() {
        super("Proposal is no longer pending");
    }
}
