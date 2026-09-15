 /*******************************************************************************\
 * Copyright (c) 2008, Claudio Pica                                              *   
 * All rights reserved.                                                          * 
 * Redistribution and use in source and binary forms, with or without            *
 * modification, are permitted provided that the following conditions are met:   *
 *                                                                               *
 * 1)  Redistributions of source code must retain the above copyright notice,    *
 *     this list of conditions and the following disclaimer.                     *
 * 2)  Redistributions in binary form must reproduce the above copyright notice, *
 *     this list of conditions and the following disclaimer in the documentation *
 *     and/or other materials provided with the distribution.                    *
 * 3)  Any publication in any form derived from the use of this software or any  *
 *     modification of it must refer explicitly to the original BSMBench package *
 *     (including the official URL http://github.com/blucini/BSMBench)           *
 *     and cite the following two publications:                                  *
 *       [1] L. Del Debbio, A. Patella, C. Pica, "Higher representations on the  *
 *           lattice: Numerical simulations. SU(2) with adjoint fermions", Phys. *
 * 	  Rev. D81 (2010) 094503, DOI 10.1103/PhysRevD.81.094503                 *
 *       [2] L. Del Debbio, B. Lucini, A. Patella, C. Pica, A. Rago, "Conformal  *
 *       versus confining scenario in SU(2) with adjoint fermions", Phys.Rev.    *
 *       D80 (2009) 074507, DOI 10.1103/PhysRevD.80.074507                       *
 * 4)  The names of the contributors may be used to endorse or promote products  *
 *     derived from this software without specific prior written permission.     *
 *                                                                               *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"   *
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE     *
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE    *
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE     *
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR           *
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF          *
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS      *
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN       *
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)       *
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF        *
 * THE POSSIBILITY OF SUCH DAMAGE.                                               *
 \*******************************************************************************/

#ifndef LOGGER_H
#define LOGGER_H

 /***** ***********************************************
 * Simple output logging facility
 * ***********************************************
 * SYNOPIS: 
 *
 * Messages outputted through the function
 * 
 * int lprintf(char *name, int level, char*, ...);
 * 
 * where name is an ID, level defines a log level,
 * can be written to file streams according to a
 * specified map. The map name => filename is 
 * defined (and can be modified) using the library
 * functions:
 *
 * int logger_map(char *name, char *filename);
 * int logger_unmap(char *name);
 * int logger_unmap_all();
 *
 * All unmapped IDs are written to the logger
 * stdout stream which can be set using:
 *
 * int logger_stdout(char *filename);
 *
 * (default = program stdout)
 *
 * ***********************************************/


 /***** map the ID name to the file with name filename 
 * filename can start with ">>" in which case the file
 * is open in append mode
 *
 * Returns:
 * 0 => success
 * 1 => invalid name
 * 2 => invalid filename
 * 3 => cannot open new file
 */
int logger_map(char *name, char *filename);

 /***** unmap the ID name
 * all subsequent calls to lprintf with this ID
 * are mapped to the logger stdout
 * If the function fails the mapping is unchanged
 *
 * Returns:
 * 0 => success
 * 1 => invalid name
 */
int logger_unmap(char *name);

 /***** reset the logger:
 * all mappings are deleted
 * all verbosity levels are deleted
 * default verbosity level is set to 0
 * logger stdout is mapped to application stdout
 */
int logger_reset();

 /***** set the logger stdout stream
 * if filename=0 this function reset the stream to the 
 * program stdout
 * NB: if filename is already open as a logger stream that stream become
 * the logger stdout and all previously names mapped to that stream now write
 * to stdout. If stdout is then changed all those names are written to a different 
 * stream.
 *
 * return codes:
 * 0 => success
 * 1 => failed to open new file (old logger stdout remains unchanged)
 */
int logger_stdout(char *filename);

 /***** set verbosity level of the logger 
 * if name==0 then set the default level
 */
void logger_setlevel(char *name, int v);
int logger_getlevel(char *name);
 /***** reset verbosity level for name to stardard level */
void logger_rmlevel(char *name);

 /***** Global enable/disable functions */
void logger_enable();
void logger_disable();

 /***** log function 
 * write to the mapped stream if message level
 * is not bigger than the logger verbosity level
 *
 * arguments:
 * name -> ID of the log stream 
 * level -> log level of the message
 * format -> the same as printf
 *
 * return value:
 * the same as printf
 *
 */
int lprintf(char *name, int level, char *format, ...);

void lflush();

#endif
