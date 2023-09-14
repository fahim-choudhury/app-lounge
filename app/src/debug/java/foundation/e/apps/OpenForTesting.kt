// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 E CORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps

/**
 * This annotation allows us to open some classes for mocking purposes while they are final in
 * release builds.
 */
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CLASS])
annotation class OpenClass

/**
 * Annotate a class with [OpenForTesting] if you want it to be extendable in debug builds.
 */
@OpenClass
@Target(allowedTargets = [AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CLASS])
annotation class OpenForTesting
