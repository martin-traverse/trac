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

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;


public class StaticValidator {

    @FunctionalInterface
    private interface ValidatorFunc <T extends Message> {
        void validate(T msg, ValidationRecorder recorder, Message parent);
    }

    private Map<Class<?>, ValidatorFunc<? extends Message>> validatorMap;

    public void registerValidator(Object validator) {

        var methods = validator.getClass().getMethods();

        for (var method : methods) {

            if (!method.getName().startsWith("validate"))
                continue;

            if (method.getParameterCount() < 2 || method.getParameterCount() > 3)
                continue;

            var paramTypes = method.getParameterTypes();

            if (!Message.class.isAssignableFrom(paramTypes[0]))
                continue;

            if (!ValidationRecorder.class.isAssignableFrom(paramTypes[1]))
                continue;

            if (paramTypes.length == 3 && !Message.class.equals(paramTypes[2]))
                continue;

            var messageType = paramTypes[0];
            validatorMap.put(messageType, buildValidator(method, validator));
        }
    }

    private <T extends Message> ValidatorFunc<T> buildValidator(Method validationFunc, Object validator) {

        if (validationFunc.getParameterCount() == 3)
            return (msg, recorder, parent) -> invokeValidator(validationFunc, validator, msg, recorder, parent);
        else
            return (msg, recorder, parent) -> invokeValidator(validationFunc, validator, msg, recorder);
    }

    private <T extends Message> void invokeValidator(
            Method validationFunc, Object validator,
            T message, ValidationRecorder recorder, Message parent) {

        try {
            validationFunc.invoke(validator, message, recorder, parent);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private <T extends Message> void invokeValidator(
            Method validationFunc, Object validator,
            T message, ValidationRecorder recorder) {

        try {
            validationFunc.invoke(validator, message, recorder);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void validateMessage(Message msg) {

        validateMessage(msg, null);
    }

    private void validateMessage(Message msg, Message parent) {

        var fields = msg.getDescriptorForType().getFields();

        for (var field : fields) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE && msg.hasField(field)) {

                var subMsg = (Message) msg.getField(field);
                validateMessage(subMsg, msg);
            }
        }

        var typeValidator = validatorMap.get(msg.getClass());

        if (typeValidator != null)
            typeValidator.validate(msg, null, parent);
    }
}
