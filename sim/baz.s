	.text
	.globl	_start
_start:
    #add a2, zero, 0xff
    #sw
	#lw  a1, 0(zero)
	lui	a0, %hi(b)
	addi	a2, a0, %lo(b)
	lui	a0, %hi(a)
	addi	a1, a0, %lo(a)
	lui	a0, %hi(c)
	addi	a0, a0, %lo(c)
	setdiff	a0, a1, a2
	lw	a1, 0(a0)
	lw	a1, 4(a0)
	lw	a1, 8(a0)
	lw	a1, 12(a0)
	setcount a3, a0
_loop:
	j _loop

	.data
	.globl	a
a:
	.word	1                               # 0x1
	.word	2                               # 0x2
	.word	4                               # 0x4
	.word	5                               # 0x5
	.word	8                               # 0x8
	.word	10                              # 0xa
	.word	4294967295                      # 0xffffffff
	.size	a, 28

	.globl	b
b:
	.word	2                               # 0x2
	.word	3                               # 0x3
	.word	4                               # 0x4
	.word	7                               # 0x7
	.word	10                              # 0xa
	.word	11                              # 0xb
	.word	12                              # 0xc
	.word	13                              # 0xd
	.word	4294967295                      # 0xffffffff
	.size	b, 36

	.bss
	.globl	c
c:
	.zero	80
	.size	c, 80

