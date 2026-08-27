package com.rekordo.services.mail;

/** One message, in the two forms it is sent as. */
public record RenderedMail(String subject, String html, String text) {}
