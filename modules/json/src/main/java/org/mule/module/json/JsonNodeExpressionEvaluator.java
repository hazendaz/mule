/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.json;

import org.mule.routing.AbstractSplitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An JSON expression evaluator that returns {@link JsonNode}'s instead of strings.
 * Arrays are still returned as a {@link List} rather than as an {@link ArrayNode} to
 * enable splitting using the {@link AbstractSplitter}
 * 
 * @see org.mule.module.json.JsonData
 * @see JsonExpressionEvaluator
 */
public class JsonNodeExpressionEvaluator extends JsonExpressionEvaluator
{
    @Override
    protected Object extractResultFromNode(JsonNode result)
    {
        if (result instanceof ValueNode)
        {
            return result.asText();
        }
        if (result instanceof ArrayNode)
        {
            List<Object> parts = new ArrayList<Object>();
            for (Iterator<JsonNode> i = (result).elements(); i.hasNext();)
            {
                JsonNode arrayNode = i.next();
                parts.add(extractResultFromNode(arrayNode));
            }
            return parts;
        }
        else
        {
            return result;
        }
    }

    @Override
    public String getName()
    {
        return "json-node";
    }
}
