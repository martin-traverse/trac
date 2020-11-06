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

import com.accenture.trac.common.metadata.BasicType;
import com.accenture.trac.common.metadata.TypeDescriptor;
import com.accenture.trac.common.metadata.Value;
import com.accenture.trac.svc.meta.validation.stateless.StatelessValidator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;


public class TypeSystemValidatorTest {

    private static IValidator validator;
    private IValidationContext ctx;

    @BeforeAll
    static void setupValidator() {
        validator = new StatelessValidator();
    }

    @BeforeEach
    void setupCtx() {
        ctx = validator.newContext();
    }

    @Test
    void typeDescriptor_blank() {

        var descriptor = TypeDescriptor.newBuilder()
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_basicTypeUnrecognized() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicTypeValue(BasicType.UNRECOGNIZED.ordinal())
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @ParameterizedTest
    @EnumSource(value = BasicType.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "UNRECOGNIZED", "BASIC_TYPE_NOT_SET", "ARRAY", "MAP"})
    void typeDescriptor_primitiveOk(BasicType basicType) {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(basicType)
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertTrue(validator.check(ctx));
    }

    @ParameterizedTest
    @EnumSource(value = BasicType.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "UNRECOGNIZED", "BASIC_TYPE_NOT_SET", "ARRAY", "MAP"})
    void typeDescriptor_primitiveWithExtras(BasicType basicType) {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(basicType)
                .setArrayType(TypeDescriptor.newBuilder())
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));

        var descriptor2 = TypeDescriptor.newBuilder()
                .setBasicType(basicType)
                .setMapType(TypeDescriptor.newBuilder())
                .build();

        var ctx2 = validator.newContext();
        validator.validate(descriptor2, ctx2);
        Assertions.assertFalse(validator.check(ctx2));
    }

    @Test
    void typeDescriptor_arrayOfPrimitive() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .setArrayType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.STRING))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertTrue(validator.check(ctx));
    }

    @Test
    void typeDescriptor_arrayOfArrays() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .setArrayType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .setArrayType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.STRING)))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertTrue(validator.check(ctx));
    }

    @Test
    void typeDescriptor_arrayNoArrayType() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_arrayBlankArrayType() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .setArrayType(TypeDescriptor.newBuilder())
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_arrayWithExtras() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.ARRAY)
                .setArrayType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.STRING))
                .setMapType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.STRING))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_mapOfPrimitive() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .setMapType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.DECIMAL))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertTrue(validator.check(ctx));
    }

    @Test
    void typeDescriptor_mapOfMap() {


        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .setMapType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .setMapType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.FLOAT)))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertTrue(validator.check(ctx));
    }

    @Test
    @Disabled("Non-uniform maps not implemented yet")
    void typeDescriptor_mapOfMixedTypes() {

        Assertions.fail("Non-uniform maps not implemented yet");
    }

    @Test
    void typeDescriptor_mapNoMapType() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_mapBlankMapType() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .setMapType(TypeDescriptor.newBuilder())
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

    @Test
    void typeDescriptor_mapWithExtras() {

        var descriptor = TypeDescriptor.newBuilder()
                .setBasicType(BasicType.MAP)
                .setMapType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.DECIMAL))
                .setArrayType(TypeDescriptor.newBuilder()
                .setBasicType(BasicType.DECIMAL))
                .build();

        validator.validate(descriptor, ctx);
        Assertions.assertFalse(validator.check(ctx));
    }

}
