/*
 * SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
 * SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
 *
 * This file is part of the Intensivate CPU Core.
 *
 * Licensed under the Intensivate Non-Commercial Hardware Source
 * License v1.0. Commercial use requires a separate written license
 * from Intensivate, Inc.
 *
 * Full license: LICENSE.md
 * Patent notice: PATENTS.md
 */

package chisel3.experimental

import chisel3._

trait HasExtModuleInline { this: ExtModule =>
  protected def setInline(fileName: String, contents: String): Unit = {
    // no-op stub for older Chisel
  }
}

object prefix {
  def apply[A](name: String)(block: => A): A = block
}