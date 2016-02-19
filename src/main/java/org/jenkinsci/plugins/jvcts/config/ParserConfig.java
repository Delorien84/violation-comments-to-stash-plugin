package org.jenkinsci.plugins.jvcts.config;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.google.common.base.Optional;
import hudson.plugins.violations.TypeDescriptor;


public class ParserConfig implements Externalizable {
    private String pattern;
    private TypeDescriptor parserTypeDescriptor;
    private String pathPrefix;

    public ParserConfig() {

    }

    public ParserConfig(TypeDescriptor typeDescriptor, String pattern) {
        this.parserTypeDescriptor = typeDescriptor;
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public TypeDescriptor getParserTypeDescriptor() {
        return parserTypeDescriptor;
    }

    public void setParserTypeDescriptor(TypeDescriptor parser) {
        this.parserTypeDescriptor = parser;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public Optional<String> getPathPrefixOpt() {
        return fromNullable(emptyToNull(nullToEmpty(pathPrefix).trim()));
    }

    /*
     * (non-Javadoc)
     *
     * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(pattern);
        out.writeObject(parserTypeDescriptor.getName());
        out.writeObject(pathPrefix);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.pattern = (String) in.readObject();
        this.parserTypeDescriptor = TypeDescriptor.TYPES.get(in.readObject());
        this.pathPrefix = (String) in.readObject();
    }
}
