/*
 * Copyright 2020 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.accenture.trac.svc.meta.validation.stateless;

import com.accenture.trac.common.metadata.*;
import com.accenture.trac.svc.meta.validation.IValidationContext;
import com.google.protobuf.Message;

import java.text.MessageFormat;
import java.util.Set;


@SuppressWarnings("unused")
public class TypeSystemValidator {

    public static void validateTypeDescriptor(TypeDescriptor descriptor, IValidationContext ctx) {

        switch (descriptor.getBasicType()) {

            case BASIC_TYPE_NOT_SET:

                ctx.recordError("Basic type not set");
                break;

            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING:
            case DECIMAL:
            case DATE:
            case DATETIME:

                validatePrimitiveDescriptor(descriptor, ctx);
                break;

            case ARRAY:

                validateArrayDescriptor(descriptor, ctx);
                break;

            case MAP:

                validateMapDescriptor(descriptor, ctx);
                break;

            default:

                ctx.recordError("Basic type not recognized");
        }
    }

    private static void validatePrimitiveDescriptor(TypeDescriptor descriptor, IValidationContext ctx) {

        var messageTemplate = MessageFormat.format(
                "Type descriptor for primitive type '{}' has extra property '{}'",
                descriptor.getBasicType().name());

        checkAllowedFields(descriptor, Set.of("basicType"), messageTemplate, ctx);
    }

    private static void validateArrayDescriptor(TypeDescriptor descriptor, IValidationContext ctx) {

        if (!descriptor.hasArrayType()) {

            var message = "Array descriptor missing arrayType property";
            ctx.recordError(message);
        }

        var messageTemplate = "Type descriptor for array has extra property '{}'";
        checkAllowedFields(descriptor, Set.of("basicType", "arrayType"), messageTemplate, ctx);
    }

    private static void validateMapDescriptor(TypeDescriptor descriptor, IValidationContext ctx) {

        if (!descriptor.hasMapType()) {

            var message = "Map descriptor missing mapType property";
            ctx.recordError(message);
        }

        var messageTemplate = "Type descriptor for map has extra property '{}'";
        checkAllowedFields(descriptor, Set.of("basicType", "mapType"), messageTemplate, ctx);
    }


    public static void validateDecimalValue(Object decimalValue, IValidationContext ctx) {

    }

    public static void validateDateValue(Object dateValue, IValidationContext ctx) {

    }

    public static void validateDatetimeValue(Object datetimeValue, IValidationContext ctx) {

    }

    public static void validateArrayValue(ArrayValue arrayValue, IValidationContext ctx) {

    }

    public static void validateMapValue(MapValue mapValue, IValidationContext ctx) {

    }

    public static void validateValue(Value value, IValidationContext ctx, Object parent) {

        var valueCaseType = TypeSystem.basicType(value.getValueCase());

        if (TypeSystem.isPrimitive(valueCaseType)) {

            if (!value.hasType())
                return;

            if (valueCaseType == value.getType().getBasicType())
                return;

            ctx.recordError("Value does not match type descriptor");  // todo message
        }

//        if (parent instanceof ArrayValue || parent instanceof MapValue)
//            ;
//
//        else
//            ;
    }

    private static void checkAllowedFields(
            Message msg, Set<String> allowedFields,
            String errorTemplate, IValidationContext ctx) {

        var allFields = msg.getAllFields();

        for (var field : allFields.keySet()) {

            if (msg.hasField(field) && !allowedFields.contains(field.getName())) {

                var message = MessageFormat.format(errorTemplate, field.getName());
                ctx.recordError(message);
            }
        }
    }

}
