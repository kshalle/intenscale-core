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

import "DPI-C" function longint memory_response (
  input bit isWrite
);

module MemoryResponseHelper #(
  parameter REQUEST_TYPE
)(
  input             clock,
  input             reset,
  input             enable,
  output reg [63:0] response
);

always @(posedge clock or posedge reset) begin
  if (reset) begin
    response <= 64'b0;
  end
  else if (!reset && enable) begin
    response <= memory_response(REQUEST_TYPE);
  end
 else begin
    response <= 64'b0;
  end
end

endmodule
