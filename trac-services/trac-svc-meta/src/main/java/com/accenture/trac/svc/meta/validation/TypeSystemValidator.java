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

package com.accenture.trac.svc.meta.validation;

import com.accenture.trac.common.metadata.*;
import com.google.protobuf.Message;


public class TypeSystemValidator {

    public void validateTypeDescriptor(TypeDescriptor descriptor, ValidationRecorder recorder) {

        if (descriptor.getBasicType() == null)
            ;

        if (descriptor.getBasicType() == BasicType.BASIC_TYPE_NOT_SET)
            ;

        if (descriptor.getBasicType() == BasicType.UNRECOGNIZED)
            ;

        if (TypeSystem.isPrimitive(descriptor.getBasicType())) {

        }
    }

    public void validateDecimalValue(Object decimalValue, ValidationRecorder recorder) {

    }

    public void validateDateValue(Object dateValue, ValidationRecorder recorder) {

    }

    public void validateDatetimeValue(Object datetimeValue, ValidationRecorder recorder) {

    }

    public void validateArrayValue(ArrayValue arrayValue, ValidationRecorder recorder) {

    }

    public void validateMapValue(MapValue mapValue, ValidationRecorder recorder) {

    }

    public void validateValue(Value value, ValidationRecorder recorder, Message parent) {

        if (parent instanceof ArrayValue || parent instanceof MapValue)
            ;

        else
            ;
    }
}
