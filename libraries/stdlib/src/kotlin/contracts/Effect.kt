/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.contracts

import kotlin.internal.ContractsDsl

/**
 * Abstract effect, the inheritors of which are used in [ContractBuilder] to describe the contract function.
 */
@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface Effect

@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface ConditionalEffect : Effect

@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface SimpleEffect : Effect {
    @ContractsDsl
    @ExperimentalContracts
    public infix fun implies(booleanExpression: Boolean): ConditionalEffect
}


@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface Returns : SimpleEffect

@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface ReturnsNotNull : SimpleEffect

@ContractsDsl
@ExperimentalContracts
@SinceKotlin("1.3")
public interface CallsInPlace : Effect