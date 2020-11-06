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

import com.accenture.trac.common.exception.EInputValidation;
import com.accenture.trac.common.exception.ETracInternal;
import com.google.protobuf.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class BaseValidator implements IValidator {

    @FunctionalInterface
    protected interface ValidationFunc<TMsg> {

        void validate(TMsg msg, IValidationContext ctx, Object data);
    }

    private static final ValidationFunc<Message> NULL_VALIDATOR = (msg, data, ctx) -> {};

    private final Map<Class<?>, ValidationFunc<Message>> validatorMap;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public BaseValidator() {
        validatorMap = new HashMap<>();
    }

    @Override
    public IValidationContext newContext() {
        return new ValidationContext();
    }

    @Override
    public void validate(Message msg, IValidationContext ctx) {

        doValidation(msg, null, ctx);
    }

    @Override
    public void validate(Message msg, Object data, IValidationContext ctx) {

        doValidation(msg, data, ctx);
    }

    @Override
    public boolean check(IValidationContext ctx) {

        return ctx.getErrors().isEmpty();
    }

    @Override
    public void checkAndThrow(IValidationContext ctx) {

        if (!ctx.getErrors().isEmpty())
            throw new EInputValidation("");  // TODO: error details
    }

    final protected void doValidation(Message msg, Object data, IValidationContext ctx) {

        var msgValidator = validatorMap.get(msg.getClass());

        // If no validation is configured for the message type, that is a bug
        // Validation must always be configured, even to explicitly turn it off
        if (msgValidator == null) {

            throw new ETracInternal("Validation is not configured for message type '" + msg.getClass() + "'");
        }

        // If validation has been explicitly turned off, log a warning
        // This will stop the validator walking any further down the object tree
        if (msgValidator == NULL_VALIDATOR) {

            log.warn("Validation has been disabled for message type '{}'", msg.getClass().getName());
            return;
        }

        var skipMain = preValidation(msg, ctx, data);

        if (!skipMain)
            msgValidator.validate(msg, ctx, data);

        postValidation(msg, ctx, data);
    }

    protected boolean preValidation(Message msg, IValidationContext ctx, Object data) {

        return false;
    }

    protected void postValidation(Message msg, IValidationContext ctx, Object data) {

    }



    @SuppressWarnings("unchecked")
    protected <TMsg extends Message> void addValidator(Class<TMsg> msgType, ValidationFunc<TMsg> func) {

        log.info("Adding validator for message type: '{}'", msgType.getSimpleName());

        var genericFunc = (ValidationFunc<Message>) func;
        validatorMap.put(msgType, genericFunc);
    }

    protected <TMsg extends Message> void addNullValidator(Class<TMsg> msgType) {

        log.info("Disabling validation for message type: '{}'", msgType.getSimpleName());

        validatorMap.put(msgType, NULL_VALIDATOR);
    }

    protected void addValidatorClass(Class<?> validatorClass) {

        var methods = Arrays.stream(validatorClass.getMethods())
                .filter(method -> method.getName().startsWith("validate"))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getParameterCount() >= 2)
                .filter(method -> method.getParameterCount() <= 3)
                .filter(method -> Message.class.isAssignableFrom(method.getParameterTypes()[0]))
                .filter(method -> IValidationContext.class.equals(method.getParameterTypes()[1]))
                .collect(Collectors.toList());

        for (var method : methods) {

            var msgType = method.getParameterTypes()[0];
            var validator = buildValidator(method, null);

            log.info("Adding validator for message type: '{}'", msgType.getSimpleName());

            validatorMap.put(msgType, validator);
        }
    }

    protected void addValidatorObject(Object validatorObject) {

        var methods = Arrays.stream(validatorObject.getClass().getMethods())
                .filter(method -> method.getName().startsWith("validate"))
                .filter(method -> method.getParameterCount() >= 2)
                .filter(method -> method.getParameterCount() <= 3)
                .filter(method -> Message.class.isAssignableFrom(method.getParameterTypes()[0]))
                .filter(method -> IValidationContext.class.equals(method.getParameterTypes()[1]))
                .collect(Collectors.toList());

        for (var method : methods) {

            var msgType = method.getParameterTypes()[0];
            var validator = buildValidator(method, validatorObject);

            log.info("Adding validator for message type: '{}'", msgType.getSimpleName());

            validatorMap.put(msgType, validator);
        }
    }

    private <TMsg extends Message> ValidationFunc<TMsg> buildValidator(Method validationFunc, Object validator) {

        if (validationFunc.getParameterCount() == 3)

            return (TMsg msg, IValidationContext ctx, Object data) -> invokeValidator(validationFunc, validator, msg, ctx, data);

        else

            return (TMsg msg, IValidationContext ctx, Object data) -> invokeValidator(validationFunc, validator, msg, ctx);
    }

    private <TMsg extends Message> void invokeValidator(
            Method validationFunc, Object validator,
            TMsg message, IValidationContext ctx, Object data) {

        try {
            validationFunc.invoke(validator, message, ctx, data);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private <TMsg extends Message> void invokeValidator(
            Method validationFunc, Object validator,
            TMsg message, IValidationContext ctx) {

        try {
            validationFunc.invoke(validator, message, ctx);
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
