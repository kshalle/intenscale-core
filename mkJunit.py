#!/usr/bin/python3
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

import os
from junit_xml import TestSuite, TestCase
import re


def find_all(pat, path):
    # print(name)
    # print(path)
    result = []
    for root, dirs, files in os.walk(path):
        print(f'root={root}, dirs={dirs}')
        result.extend([os.path.join(root, f) for f in files if pat.match(f)])
    return result


logfiles = find_all(re.compile('.*.out$'), 'emulator/output')
print(f"Processing the following logfiles{logfiles}")
RE = "PASSED"
test_cases = []
for file in logfiles:
    with open(file) as f:
        tc = TestCase(file, 'intenscale', 0, '', '')
        passed = False
        for line in f:
            if re.search(RE, line):
                passed = True
        if not passed:
            tc.add_error_info(message="Test Failed", error_type="CRITICAL")
        test_cases.append(tc)
ts = TestSuite("Firmware test suite", test_cases)
with open('junit.xml', 'w') as f:
    TestSuite.to_file(f, [ts], prettyprint=False)
