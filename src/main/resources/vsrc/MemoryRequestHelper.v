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

import "DPI-C" function bit memory_request (
  input longint address,
  input int id,
  input bit isWrite
);

module MemoryRequestHelper #(
  parameter REQUEST_TYPE
)(
  input             clock,
  input             reset,
  input             io_req_valid,
  input      [63:0] io_req_bits_addr,
  input      [31:0] io_req_bits_id,
  output reg        io_response
);

always @(posedge clock or posedge reset) begin
  if (reset) begin
    io_response <= 1'b0;
  end
  else if (io_req_valid) begin
    io_response <= memory_request(io_req_bits_addr, io_req_bits_id, REQUEST_TYPE);
  end  else begin
    io_response <= 1'b0;
  end
end

endmodule
