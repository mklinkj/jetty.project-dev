//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.EnumSet.allOf;
import static java.util.EnumSet.complementOf;
import static java.util.EnumSet.copyOf;
import static java.util.EnumSet.noneOf;
import static java.util.EnumSet.of;

/**
 * HTTP compliance modes for Jetty HTTP parsing and handling.
 * A Compliance mode consists of a set of {@link Violation}s which are applied
 * when the mode is enabled.
 */
public final class HttpCompliance implements ComplianceViolation.Mode
{

    // These are compliance violations, which may optionally be allowed by the compliance mode, which mean that
    // the relevant section of the RFC is not strictly adhered to.
    public enum Violation implements ComplianceViolation
    {
        CASE_SENSITIVE_FIELD_NAME("https://tools.ietf.org/html/rfc7230#section-3.2", "Field name is case-insensitive"),
        CASE_INSENSITIVE_METHOD("https://tools.ietf.org/html/rfc7230#section-3.1.1", "Method is case-sensitive"),
        HTTP_0_9("https://tools.ietf.org/html/rfc7230#appendix-A.2", "HTTP/0.9 not supported"),
        MULTILINE_FIELD_VALUE("https://tools.ietf.org/html/rfc7230#section-3.2.4", "Line Folding not supported"),
        MULTIPLE_CONTENT_LENGTHS("https://tools.ietf.org/html/rfc7230#section-3.3.1", "Multiple Content-Lengths"),
        TRANSFER_ENCODING_WITH_CONTENT_LENGTH("https://tools.ietf.org/html/rfc7230#section-3.3.1", "Transfer-Encoding and Content-Length"),
        WHITESPACE_AFTER_FIELD_NAME("https://tools.ietf.org/html/rfc7230#section-3.2.4", "Whitespace not allowed after field name"),
        NO_COLON_AFTER_FIELD_NAME("https://tools.ietf.org/html/rfc7230#section-3.2", "Fields must have a Colon");

        private final String url;
        private final String description;

        Violation(String url, String description)
        {
            this.url = url;
            this.description = description;
        }

        @Override
        public String getName()
        {
            return name();
        }

        @Override
        public String getURL()
        {
            return url;
        }

        @Override
        public String getDescription()
        {
            return description;
        }
    }

    public final static HttpCompliance RFC7230 = new HttpCompliance("RFC7230", noneOf(Violation.class));
    public final static HttpCompliance RFC2616 = new HttpCompliance("RFC2616", of(Violation.HTTP_0_9, Violation.MULTILINE_FIELD_VALUE));
    public final static HttpCompliance LEGACY = new HttpCompliance("LEGACY", complementOf(of(Violation.CASE_INSENSITIVE_METHOD)));
    public final static HttpCompliance RFC2616_LEGACY = RFC2616.with( "RFC2616_LEGACY",
            Violation.CASE_INSENSITIVE_METHOD,
            Violation.NO_COLON_AFTER_FIELD_NAME,
            Violation.TRANSFER_ENCODING_WITH_CONTENT_LENGTH,
            Violation.MULTIPLE_CONTENT_LENGTHS);
    public final static HttpCompliance RFC7230_LEGACY = RFC7230.with("RFC7230_LEGACY", Violation.CASE_INSENSITIVE_METHOD);

    public static final String VIOLATIONS_ATTR = "org.eclipse.jetty.http.compliance.violations";

    private static final Logger LOG = Log.getLogger(HttpParser.class);
    private static EnumSet<Violation> violationByProperty(String property)
    {
        String s = System.getProperty(HttpCompliance.class.getName()+property);
        return violationBySpec(s==null?"*":s);
    }



    /**
     * Create violation set from string
     * <p>
     * @param spec A string in the format of a comma separated list starting with one of the following strings:<dl>
     * <dt>0</dt><dd>No {@link Violation}s</dd>
     * <dt>*</dt><dd>All {@link Violation}s</dd>
     * <dt>RFC2616</dt><dd>The set of {@link Violation}s application to https://tools.ietf.org/html/rfc2616,
     * but not https://tools.ietf.org/html/rfc7230</dd>
     * <dt>RFC7230</dt><dd>The set of {@link Violation}s application to https://tools.ietf.org/html/rfc7230</dd>
     * </dl>
     * The remainder of the list can contain then names of {@link Violation}s to include them in the mode, or prefixed
     * with a '-' to exclude thm from the mode.
     * <p>
     */
    static EnumSet<Violation> violationBySpec(String spec)
    {
        EnumSet<Violation> sections;
        String[] elements = spec.split("\\s*,\\s*");
        int i=0;

        switch(elements[i])
        {
            case "0":
                sections = noneOf(Violation.class);
                i++;
                break;

            case "*":
                sections = allOf(Violation.class);
                i++;
                break;

            case "RFC2616":
                sections = copyOf(RFC2616.getAllowed());
                i++;
                break;

            case "RFC7230":
                sections = copyOf(RFC7230.getAllowed());
                i++;
                break;

            default:
                sections = noneOf(Violation.class);
                break;
        }

        while(i<elements.length)
        {
            String element = elements[i++];
            boolean exclude = element.startsWith("-");
            if (exclude)
                element = element.substring(1);
            Violation section = Violation.valueOf(element);
            if (section==null)
            {
                LOG.warn("Unknown section '"+element+"' in HttpCompliance spec: "+spec);
                continue;
            }
            if (exclude)
                sections.remove(section);
            else
                sections.add(section);

        }

        return sections;
    }


    private final String _name;
    private final Set<Violation> _violations;

    private HttpCompliance(String name, Set<Violation> violations)
    {
        Objects.nonNull(violations);
        _name = name;
        _violations = unmodifiableSet(copyOf(violations));
    }

    @Override
    public boolean allows(ComplianceViolation violation)
    {
        return _violations.contains(violation);
    }

    @Override
    public String getName()
    {
        return _name;
    }

    /**
     * Get the set of {@link Violation}s allowed by this compliance mode.
     * @return The immutable set of {@link Violation}s allowed by this compliance mode.
     */
    @Override
    public Set<Violation> getAllowed()
    {
        return _violations;
    }

    @Override
    public Set<Violation> getKnown()
    {
        return EnumSet.allOf(Violation.class);
    }

    // TODO javadoc
    public HttpCompliance with(String name, Violation... violations)
    {
        EnumSet<Violation> union = _violations.isEmpty()?EnumSet.noneOf(Violation.class):copyOf(_violations);
        union.addAll(copyOf(asList(violations)));
        return new HttpCompliance(name, union);
    }

    // TODO javadoc
    public HttpCompliance without(String name, Violation... violations)
    {
        EnumSet<Violation> remainder = _violations.isEmpty()?EnumSet.noneOf(Violation.class):copyOf(_violations);
        remainder.removeAll(copyOf(asList(violations)));
        return new HttpCompliance(name, remainder);
    }

    @Override
    public String toString()
    {
        return String.format("%s%s",_name,_violations);
    }
}
