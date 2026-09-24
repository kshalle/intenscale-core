# SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
# SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
#
# This file is part of the Intensivate CPU Core.
#
# Licensed under the Intensivate Non-Commercial Hardware Source
# License v1.0. Commercial use requires a separate written license
# from Intensivate, Inc.
#
# Full license: LICENSE.md
# Patent notice: PATENTS.md

# You may need to update the following numbers depending on the clock speed and benchmark.

# Stop om failure.
assertion -simstop -all -severity failure

# For Dhrystone the start time is 1400us for 500MHz clock.

# Set the start dumping time.
run 1400us

# Start VCD dumping
database -open waves -into waves -default
probe -create -vcd -all -variables -depth all

# Start SAIF tile dump.
dumpsaif -scope rocketTestHarness/dut/RocketTile -overwrite -hierarchy -internal -output test.saif

run 200us
exit






