// See LICENSE for license details.

#include <stdint.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>
#include <limits.h>
#include <sys/signal.h>
#include "util.h"

#define SYS_write 64
#define SYS_getmainvars 2011

#undef strcmp

extern volatile uint64_t tohost;
extern volatile uint64_t fromhost;
extern volatile uint64_t hostAccessSema;
extern volatile uint64_t rowActive;
extern volatile uint64_t rowFail;

int printf(const char* fmt, ...);



static inline void arch_rand_delay(int failCntr) {
	int randomDelay;

	// Add some random delay. hartId spacing and the mask range are widened
	// (vs. the original hartId<<2 & 0x1ff) so hart backoff delays stay spread
	// apart without wrapping/aliasing as hart count grows towards 64 --
	// hartId<<4 spans up to 63*16=1008, safely under the 0x7ff (2047) mask.
	int hartId = read_csr(mhartid);
	randomDelay = ((((failCntr >> 2) << 4) + (hartId << 4)) & 0x7ff) + 0x7f;
	delay_cycles(randomDelay);
}

static void wait_host_free()
{
  int tmp;
  int failCntr = 0;

  while(1) {
    int success = 1;
    __asm__ __volatile__(
      "1:  lr.w  %1, %0\n"
      "  bnez  %1, 2f\n"
      "  li  %1, -1\n"
      "  sc.w.aq  %1, %1, %0\n"
      "  beqz  %1, 3f\n"
      "2:  li %2, 0\n"
      "3:\n"
      : "+A" (hostAccessSema), "=&r" (tmp), "+&r" (success)
      :: "memory");
    if(success) {
      break;
    }
    __asm__ __volatile__("lw zero, %0" : "+A" (hostAccessSema) :: "memory");
    arch_rand_delay(failCntr);
    failCntr++;
  }
}

static uintptr_t syscall(uintptr_t which, uint64_t arg0, uint64_t arg1, uint64_t arg2)
{
  volatile uint64_t magic_mem[8] __attribute__((aligned(64)));
  magic_mem[0] = which;
  magic_mem[1] = arg0;
  magic_mem[2] = arg1;
  magic_mem[3] = arg2;
  __sync_synchronize();
  wait_host_free();

  tohost = (uintptr_t)magic_mem;
  while (fromhost == 0);
  fromhost = 0;

  __asm__ __volatile__ ("amoswap.w.rl x0, x0, %0" : "=A" (hostAccessSema) :: "memory");
  return magic_mem[0];
}

#define NUM_COUNTERS 2
static uintptr_t counters[NUM_COUNTERS * NUM_ROW];
static char* counter_names[NUM_COUNTERS];
static uint64_t argv_buf[256];

void setStats(int enable)
{
  int i = 0;
  int coreId = read_csr_safe(mhartid);

#ifndef SPIKE
  if((coreId == 0) && (enable == 1)) {
    // Disable the debug mode.
    asm volatile ("csrw	0xc, %0;" :: "r"(0x1));
  }
#endif

#define READ_CTR(name) do { \
    while (i >= NUM_COUNTERS) ; \
    uintptr_t csr = read_csr(name); \
    if (!enable) { csr -= counters[(coreId * NUM_COUNTERS) + i]; counter_names[i] = #name; } \
    counters[(coreId * NUM_COUNTERS) + (i++)] = csr; \
  } while (0)

  READ_CTR(mcycle);
  READ_CTR(minstret);

#ifndef SPIKE
  if((coreId == 0) && (enable == 0)) {
    // RE-enable the debug mode.
    asm volatile ("csrw	0xc, %0;" :: "r"(0x0));
  }
#endif

#undef READ_CTR
}

void __attribute__((noreturn)) tohost_exit(uintptr_t code)
{
  // Get the address.
  int coreId = read_csr_safe(mhartid);

  asm volatile ("amoor.d %0, %0, 0(%1);" :: "r" (code<<coreId), "r" (&rowFail));
  asm volatile ("amoand.d %0, %0, 0(%1);" :: "r" (~(1<<coreId)), "r" (&rowActive));

  // Only first thread can send exit code.
  if(coreId == 0) {
    while(rowActive) {
      int delay = 128;
      delay_cycles(delay);
    }

    wait_host_free();
    tohost = (rowFail<<1) + 1;
  }

  // Infinite loop.
  while(1);
}

trap_handler exception_handlers[MAX_EXCEPTION_CODE+1];
trap_handler interrupt_handlers[MAX_INTERRUPT_CODE+1];

uintptr_t __attribute__((weak)) handle_trap(uint64_t cause, uintptr_t epc, uint64_t regs[32]) {
  int64_t trapno = cause & 0x7fffffffffffffff;
  trap_handler handler = NULL;
  if((int64_t)cause < 0) { // high bit set?
    // interrupt
    if(trapno <= MAX_INTERRUPT_CODE) handler = interrupt_handlers[trapno];
  } else {
    // exception
    if(trapno <= MAX_EXCEPTION_CODE) handler = exception_handlers[trapno];
  }
  if(handler) return handler(cause,epc,regs);
  
  printf("hart %2d unhandled trap 0x%lx at %p, aborting\n",read_csr_safe(mhartid),cause,epc);
	abort();
}

void __attribute__((noreturn)) exit(int code)
{
  tohost_exit(code);
}

// This is used for early exit on error.
// Specifically, it aborts the program without waiting for all threads
// to finish.
// This is expected to trigger a dump in firesim.
void __attribute__((noreturn)) abort()
{
  uint64_t mcycle = read_csr(mcycle);
  printf("ABORT at cycle %d\n",mcycle);

  // Get the address.
  int coreId = read_csr_safe(mhartid);

#ifdef TRACE_DUMP_ON_ABORT
  trace_dump();
#else
  tohost = 841;
#endif
  while(1);
}

// returns 0 on success, non-0 if buffer is too small
int getmainvars(void* buf, uint64_t len) {
  return syscall(SYS_getmainvars,(uint64_t)buf,len,0);
}

void printstr(const char* s)
{
  syscall(SYS_write, 1, (uintptr_t)s, strlen(s));
}

int __attribute__((weak)) extra_thread_entry(int cid, int nc)
{
  return 0;
}

// single-threaded programs override this function.
int __attribute__((weak)) main(int argc, char** argv)
{
  printstr("Implement main(), foo!\n");
  abort();
}

// multi-threaded programs override this function.
// nc is currently invalid
int __attribute__((weak)) thread_entry(int cid, int nc)
{
  // hart 0 calls main()
  if(cid == 0) {
    int ret = getmainvars(argv_buf,sizeof(argv_buf));
    if(ret) {
      printf("Argument list too long (error %d)\n",ret);
      abort();
    }
    return main((int)argv_buf[0],(char**)&argv_buf[1]);
  }
  
  // Other harts call extra_thread_entry()
  return extra_thread_entry(cid,nc);
}

static void init_tls()
{
  register void* thread_pointer asm("tp");
  extern char _tls_data;
  extern __thread char _tdata_begin, _tdata_end, _tbss_end;
  size_t tdata_size = &_tdata_end - &_tdata_begin;
  memcpy(thread_pointer, &_tls_data, tdata_size);
  size_t tbss_size = &_tbss_end - &_tdata_end;
  memset(thread_pointer + tdata_size, 0, tbss_size);
}

static unsigned char args[1024];

void _init(int cid, int nc)
{
  init_tls();

  int ret = thread_entry(cid, nc);

  char buf[NUM_COUNTERS * 32] __attribute__((aligned(64)));
  char* pbuf = buf;
  int coreId = read_csr_safe(mhartid);

  for (int i = 0; i < NUM_COUNTERS; i++) {
    if (counters[i]) {
      pbuf += sprintf(pbuf, "R_%x: %s = %d\n", coreId, counter_names[i], counters[i + (NUM_COUNTERS * coreId)]);
    }
  }
  if (pbuf != buf) {
    printstr(buf);
  }

  exit(ret);
}

#undef putchar
int putchar(int ch)
{
  static __thread char buf[256] __attribute__((aligned(64)));
  static __thread int buflen = 0;

  buf[buflen++] = ch;

  if (ch == '\n' || buflen == sizeof(buf))
  {
    syscall(SYS_write, 1, (uintptr_t)buf, buflen);
    buflen = 0;
  }

  return 0;
}

void printhex(uint64_t x)
{
  char str[17];
  int i;
  for (i = 0; i < 16; i++)
  {
    str[15-i] = (x & 0xF) + ((x & 0xF) < 10 ? '0' : 'a'-10);
    x >>= 4;
  }
  str[16] = 0;

  printstr(str);
}

static inline void printnum(void (*putch)(int, void**), void **putdat,
                    unsigned long long num, unsigned base, int width, int padc)
{
  unsigned digs[sizeof(num)*CHAR_BIT];
  int pos = 0;

  while (1)
  {
    digs[pos++] = num % base;
    if (num < base)
      break;
    num /= base;
  }

  while (width-- > pos)
    putch(padc, putdat);

  while (pos-- > 0)
    putch(digs[pos] + (digs[pos] >= 10 ? 'a' - 10 : '0'), putdat);
}

static unsigned long long getuint(va_list *ap, int lflag)
{
  if (lflag >= 2)
    return va_arg(*ap, unsigned long long);
  else if (lflag)
    return va_arg(*ap, unsigned long);
  else
    return va_arg(*ap, unsigned int);
}

static long long getint(va_list *ap, int lflag)
{
  if (lflag >= 2)
    return va_arg(*ap, long long);
  else if (lflag)
    return va_arg(*ap, long);
  else
    return va_arg(*ap, int);
}

static void vprintfmt(void (*putch)(int, void**), void **putdat, const char *fmt, va_list ap)
{
  register const char* p;
  const char* last_fmt;
  register int ch, err;
  unsigned long long num;
  int base, lflag, width, precision, altflag;
  char padc;

  while (1) {
    while ((ch = *(unsigned char *) fmt) != '%') {
      if (ch == '\0')
        return;
      fmt++;
      putch(ch, putdat);
    }
    fmt++;

    // Process a %-escape sequence
    last_fmt = fmt;
    padc = ' ';
    width = -1;
    precision = -1;
    lflag = 0;
    altflag = 0;
  reswitch:
    switch (ch = *(unsigned char *) fmt++) {

    // flag to pad on the right
    case '-':
      padc = '-';
      goto reswitch;
      
    // flag to pad with 0's instead of spaces
    case '0':
      padc = '0';
      goto reswitch;

    // width field
    case '1':
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case '7':
    case '8':
    case '9':
      for (precision = 0; ; ++fmt) {
        precision = precision * 10 + ch - '0';
        ch = *fmt;
        if (ch < '0' || ch > '9')
          break;
      }
      goto process_precision;

    case '*':
      precision = va_arg(ap, int);
      goto process_precision;

    case '.':
      if (width < 0)
        width = 0;
      goto reswitch;

    case '#':
      altflag = 1;
      goto reswitch;

    process_precision:
      if (width < 0)
        width = precision, precision = -1;
      goto reswitch;

    // long flag (doubled for long long)
    case 'l':
      lflag++;
      goto reswitch;

    // character
    case 'c':
      putch(va_arg(ap, int), putdat);
      break;

    // string
    case 's':
      if ((p = va_arg(ap, char *)) == NULL)
        p = "(null)";
      if (width > 0 && padc != '-')
        for (width -= strnlen(p, precision); width > 0; width--)
          putch(padc, putdat);
      for (; (ch = *p) != '\0' && (precision < 0 || --precision >= 0); width--) {
        putch(ch, putdat);
        p++;
      }
      for (; width > 0; width--)
        putch(' ', putdat);
      break;

    // (signed) decimal
    case 'd':
      num = getint(&ap, lflag);
      if ((long long) num < 0) {
        putch('-', putdat);
        num = -(long long) num;
      }
      base = 10;
      goto signed_number;

    // unsigned decimal
    case 'u':
      base = 10;
      goto unsigned_number;

    // (unsigned) octal
    case 'o':
      // should do something with padding so it's always 3 octits
      base = 8;
      goto unsigned_number;

    // pointer
    case 'p':
      static_assert(sizeof(long) == sizeof(void*));
      lflag = 1;
      putch('0', putdat);
      putch('x', putdat);
      /* fall through to 'x' */

    // (unsigned) hexadecimal
    case 'x':
      base = 16;
    unsigned_number:
      num = getuint(&ap, lflag);
    signed_number:
      printnum(putch, putdat, num, base, width, padc);
      break;

    // escaped '%' character
    case '%':
      putch(ch, putdat);
      break;
      
    // unrecognized escape sequence - just print it literally
    default:
      putch('%', putdat);
      fmt = last_fmt;
      break;
    }
  }
}

int printf(const char* fmt, ...)
{
  va_list ap;
  va_start(ap, fmt);

  vprintfmt((void*)putchar, 0, fmt, ap);

  va_end(ap);
  return 0; // incorrect return value, but who cares, anyway?
}

int sprintf(char* str, const char* fmt, ...)
{
  va_list ap;
  char* str0 = str;
  va_start(ap, fmt);

  void sprintf_putch(int ch, void** data)
  {
    char** pstr = (char**)data;
    **pstr = ch;
    (*pstr)++;
  }

  vprintfmt(sprintf_putch, (void**)&str, fmt, ap);
  *str = 0;

  va_end(ap);
  return str - str0;
}

void* memcpy(void* dest, const void* src, size_t len)
{
  if ((((uintptr_t)dest | (uintptr_t)src | len) & (sizeof(uintptr_t)-1)) == 0) {
    const uintptr_t* s = src;
    uintptr_t *d = dest;
    while (d < (uintptr_t*)(dest + len))
      *d++ = *s++;
  } else {
    const char* s = src;
    char *d = dest;
    while (d < (char*)(dest + len))
      *d++ = *s++;
  }
  return dest;
}

// gcc 9.4.0 (ubuntu focal), when passed -O3, copmiles this into
// something that appears to consume stack space proportional to the
// size of the mem range being set
void* memset(void* dest, int byte, size_t len)
{
  if ((((uintptr_t)dest | len) & (sizeof(uintptr_t)-1)) == 0) {
    uintptr_t word = byte & 0xFF;
    word |= word << 8;
    word |= word << 16;
    word |= word << 16 << 16;

    uintptr_t *d = dest;
    while (d < (uintptr_t*)(dest + len))
      *d++ = word;
  } else {
    char *d = dest;
    while (d < (char*)(dest + len))
      *d++ = byte;
  }
  return dest;
}

size_t strlen(const char *s)
{
  const char *p = s;
  while (*p)
    p++;
  return p - s;
}

size_t strnlen(const char *s, size_t n)
{
  const char *p = s;
  while (n-- && *p)
    p++;
  return p - s;
}

int strcmp(const char* s1, const char* s2)
{
  unsigned char c1, c2;

  do {
    c1 = *s1++;
    c2 = *s2++;
  } while (c1 != 0 && c1 == c2);

  return c1 - c2;
}

char* strcpy(char* dest, const char* src)
{
  char* d = dest;
  while ((*d++ = *src++))
    ;
  return dest;
}

long atol(const char* str)
{
  long res = 0;
  int sign = 0;

  while (*str == ' ')
    str++;

  if (*str == '-' || *str == '+') {
    sign = *str == '-';
    str++;
  }

  while (*str) {
    res *= 10;
    res += *str++ - '0';
  }

  return sign ? -res : res;
}

