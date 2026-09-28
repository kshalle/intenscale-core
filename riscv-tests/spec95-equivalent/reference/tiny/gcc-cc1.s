gcc2_compiled.:
___gnu_compiled_c:
.text
	.align 4
	.proc	05
_fold0:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l0
	add %l0,3,%o0
	call .umul,0
	add %i1,-5,%o1
	mov %o0,%i0
	sll %i2,2,%o0
	add %i0,%o0,%i0
	mov %l0,%o0
	call .umul,0
	mov %i1,%o1
	or %i2,1,%o1
	call .div,0
	add %o1,7,%o1
	sub %i0,%o0,%i0
	xor %l0,%i1,%o0
	and %o0,255,%o0
	xor %i1,%i2,%i2
	call .umul,0
	or %i2,16,%o1
	add %i0,%o0,%i0
	and %i1,31,%i1
	mov %l0,%o0
	call .rem,0
	add %i1,1,%o1
	sub %i0,%o0,%i0
	ret
	restore
	.align 4
	.proc	04
_loop0:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l3
	mov 0,%i0
	cmp %i0,%i1
	bge L4
	mov 0,%l2
	sethi %hi(1000000),%o0
	or %o0,%lo(1000000),%l4
	sll %l2,2,%o1
L12:
	add %l2,1,%o0
	call .umul,0
	ld [%l3+%o1],%o1
	add %i0,%o0,%i0
	cmp %l2,%i1
	bge L7
	mov %l2,%l1
L9:
	sll %l1,2,%l0
	mov %l2,%o0
	call .umul,0
	mov %l1,%o1
	ld [%l3+%l0],%o1
	add %o0,%o1,%o0
	add %l1,2,%l1
	cmp %l1,%i1
	bl L9
	xor %i0,%o0,%i0
L7:
	cmp %i0,%l4
	bg,a L5
	sra %i0,3,%i0
L5:
	add %l2,1,%l2
	cmp %l2,%i1
	bl L12
	sll %l2,2,%o1
L4:
	ret
	restore
	.align 4
	.proc	05
_walk0:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	orcc %i0,%g0,%l0
	be L15
	mov 0,%i0
	sethi %hi(L23),%o0
	or %o0,%lo(L23),%l1
	ld [%l0],%o0
L28:
	call .rem,0
	mov 6,%o1
	cmp %o0,4
	bgu L22
	sll %o0,2,%o0
	ld [%o0+%l1],%o0
	jmp %o0
	nop
L23:
	.word	L17
	.word	L18
	.word	L19
	.word	L20
	.word	L21
L17:
	b L26
	ld [%l0+4],%o0
L18:
	ld [%l0+4],%o0
	sll %o0,1,%o0
	b L16
	sub %i0,%o0,%i0
L19:
	ld [%l0+4],%o0
	b L16
	xor %i0,%o0,%i0
L20:
	ld [%l0+4],%o0
	mov %i0,%o1
	call _fold0,0
	mov %i1,%o2
	b L16
	add %i0,%o0,%i0
L21:
	sll %i0,1,%o1
	ld [%l0+4],%o0
	and %o0,1,%o0
	b L16
	or %o1,%o0,%i0
L22:
	ld [%l0],%o0
	call .rem,0
	mov 15,%o1
	add %l0,%o0,%o0
	ldsb [%o0+12],%o0
L26:
	add %i0,%o0,%i0
L16:
	cmp %i1,0
	ble,a L27
	ld [%l0+8],%l0
	ld [%l0+8],%o0
	cmp %o0,0
	be,a L27
	ld [%l0+8],%l0
	call _walk0,0
	add %i1,-1,%o1
	add %i0,%o0,%i0
	ld [%l0+8],%l0
L27:
	cmp %l0,0
	bne,a L28
	ld [%l0],%o0
L15:
	ret
	restore
	.align 4
	.global _entry0
	.proc	04
_entry0:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l2
	mov 0,%i0
	mov 0,%l1
	sethi %hi(_table0),%o0
	or %o0,%lo(_table0),%l3
L33:
	sll %l1,2,%l0
	mov %l1,%o0
	call .umul,0
	mov %l1,%o1
	sub %o0,%l2,%o0
	add %l1,1,%l1
	cmp %l1,63
	ble L33
	st %o0,[%l0+%l3]
	cmp %l2,64
	ble L34
	mov %l2,%o1
	mov 64,%o1
L34:
	sethi %hi(_table0),%o0
	call _loop0,0
	or %o0,%lo(_table0),%o0
	add %i0,%o0,%i0
	mov %i1,%o0
	call _walk0,0
	mov 3,%o1
	add %i0,%o0,%i0
	mov %l2,%o0
	mov %i0,%o1
	call _fold0,0
	mov %l1,%o2
	ret
	restore %i0,%o0,%o0
	.align 4
	.proc	05
_fold1:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l0
	add %l0,3,%o0
	call .umul,0
	add %i1,-5,%o1
	mov %o0,%i0
	sll %i2,2,%o0
	add %i0,%o0,%i0
	mov %l0,%o0
	call .umul,0
	mov %i1,%o1
	or %i2,1,%o1
	call .div,0
	add %o1,7,%o1
	sub %i0,%o0,%i0
	xor %l0,%i1,%o0
	and %o0,255,%o0
	xor %i1,%i2,%i2
	call .umul,0
	or %i2,16,%o1
	add %i0,%o0,%i0
	and %i1,31,%i1
	mov %l0,%o0
	call .rem,0
	add %i1,1,%o1
	sub %i0,%o0,%i0
	ret
	restore
	.align 4
	.proc	04
_loop1:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l3
	mov 0,%i0
	cmp %i0,%i1
	bge L38
	mov 0,%l2
	sethi %hi(1000000),%o0
	or %o0,%lo(1000000),%l4
	sll %l2,2,%o1
L46:
	add %l2,1,%o0
	call .umul,0
	ld [%l3+%o1],%o1
	add %i0,%o0,%i0
	cmp %l2,%i1
	bge L41
	mov %l2,%l1
L43:
	sll %l1,2,%l0
	mov %l2,%o0
	call .umul,0
	mov %l1,%o1
	ld [%l3+%l0],%o1
	add %o0,%o1,%o0
	add %l1,2,%l1
	cmp %l1,%i1
	bl L43
	xor %i0,%o0,%i0
L41:
	cmp %i0,%l4
	bg,a L39
	sra %i0,3,%i0
L39:
	add %l2,1,%l2
	cmp %l2,%i1
	bl L46
	sll %l2,2,%o1
L38:
	ret
	restore
	.align 4
	.proc	05
_walk1:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	orcc %i0,%g0,%l0
	be L49
	mov 0,%i0
	sethi %hi(L57),%o0
	or %o0,%lo(L57),%l1
	ld [%l0],%o0
L62:
	call .rem,0
	mov 6,%o1
	cmp %o0,4
	bgu L56
	sll %o0,2,%o0
	ld [%o0+%l1],%o0
	jmp %o0
	nop
L57:
	.word	L51
	.word	L52
	.word	L53
	.word	L54
	.word	L55
L51:
	b L60
	ld [%l0+4],%o0
L52:
	ld [%l0+4],%o0
	sll %o0,1,%o0
	b L50
	sub %i0,%o0,%i0
L53:
	ld [%l0+4],%o0
	b L50
	xor %i0,%o0,%i0
L54:
	ld [%l0+4],%o0
	mov %i0,%o1
	call _fold1,0
	mov %i1,%o2
	b L50
	add %i0,%o0,%i0
L55:
	sll %i0,1,%o1
	ld [%l0+4],%o0
	and %o0,1,%o0
	b L50
	or %o1,%o0,%i0
L56:
	ld [%l0],%o0
	call .rem,0
	mov 15,%o1
	add %l0,%o0,%o0
	ldsb [%o0+12],%o0
L60:
	add %i0,%o0,%i0
L50:
	cmp %i1,0
	ble,a L61
	ld [%l0+8],%l0
	ld [%l0+8],%o0
	cmp %o0,0
	be,a L61
	ld [%l0+8],%l0
	call _walk1,0
	add %i1,-1,%o1
	add %i0,%o0,%i0
	ld [%l0+8],%l0
L61:
	cmp %l0,0
	bne,a L62
	ld [%l0],%o0
L49:
	ret
	restore
	.align 4
	.global _entry1
	.proc	04
_entry1:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%l2
	mov 0,%i0
	mov 0,%l1
	sethi %hi(_table1),%o0
	or %o0,%lo(_table1),%l3
L67:
	sll %l1,2,%l0
	mov %l1,%o0
	call .umul,0
	mov %l1,%o1
	sub %o0,%l2,%o0
	add %l1,1,%l1
	cmp %l1,63
	ble L67
	st %o0,[%l0+%l3]
	cmp %l2,64
	ble L68
	mov %l2,%o1
	mov 64,%o1
L68:
	sethi %hi(_table1),%o0
	call _loop1,0
	or %o0,%lo(_table1),%o0
	add %i0,%o0,%i0
	mov %i1,%o0
	call _walk1,0
	mov 3,%o1
	add %i0,%o0,%i0
	mov %l2,%o0
	mov %i0,%o1
	call _fold1,0
	mov %l1,%o2
	ret
	restore %i0,%o0,%o0
	.align 4
	.global _drive
	.proc	04
_drive:
	!#PROLOGUE# 0
	save %sp,-112,%sp
	!#PROLOGUE# 1
	mov %i0,%o0
	call _entry0,0
	mov 0,%o1
	mov %o0,%l0
	add %i0,1,%o0
	call _entry1,0
	mov 0,%o1
	ret
	restore %l0,%o0,%o0

	.reserve _table0,256,"bss"

	.reserve _table1,256,"bss"
