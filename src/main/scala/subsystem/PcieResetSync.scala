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

package freechips.rocketchip.subsystem

import Chisel._
import chisel3.{AsyncReset,RequireAsyncReset}
import chisel3.util.{Cat}
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug,HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket.constants.StConfiguration._
import freechips.rocketchip.rocket.constants._
import freechips.rocketchip.rocket.{TracedInstruction}
import CoherenceManagerWrapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

class reset_synchronizer extends BlackBox {
  val io = IO(new Bundle{ 
    val  axi_clk 		             = Input(Clock())
    val  axi_reset_n                 = Input(AsyncReset())
    val  clk			             = Input(Clock())
    val  core_clk		             = Input(Clock())
    val  mgmt_reset_n	             = Input(AsyncReset())
    val  mgmt_sticky_reset_n         = Input(AsyncReset())
    val  phy_apb_clk		         = Input(Clock())
    val  phy_apb_reset_n	         = Input(AsyncReset())
    val  phy_ctrl_apb_clk	         = Input(Clock())
    val  phy_ctrl_apb_reset_n        = Input(AsyncReset())
    val  pipe_clk		             = Input(Clock())
    val  pipe_reset_n                = Input(AsyncReset())
    val  pm_clk 			         = Input(Clock())
    val  pm_reset_n 		         = Input(AsyncReset())
    val  reset_n 		             = Input(AsyncReset())
    val  axi_reset_n_sync            = Output(AsyncReset())
    val  mgmt_reset_n_sync           = Output(AsyncReset())
    val  mgmt_sticky_reset_n_sync    = Output(AsyncReset())
    val  phy_apb_reset_n_sync        = Output(AsyncReset())
    val  phy_ctrl_apb_reset_n_sync   = Output(AsyncReset())
    val  pipe_reset_n_sync           = Output(AsyncReset())
    val  pm_reset_n_sync             = Output(AsyncReset())
    val  reset_n_sync		         = Output(AsyncReset())
    })
}
