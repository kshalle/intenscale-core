;;; SPDX-FileCopyrightText: 2026 Intensivate, Inc.
;;; SPDX-License-Identifier: BSD-2-Clause

;;; xlisp-bench-ref.lsp -- workload for the Lisp interpreter benchmark.
;;;
;;; Drives deep non-tail recursion, cons-cell churn against the collector,
;;; symbol lookup and destructive list surgery.  tak and deriv are classic
;;; Lisp benchmark kernels; the naive insertion sort and the reverse/mapcar
;;; churn keep the allocator busy so that garbage collection is part of what
;;; gets measured.
;;;
;;; Size: ref -- about 470 M instructions on spike.
;;; The three variants differ only in the four parameters below.

(setq *tak-args*   '(18 12 6))
(setq *deriv-reps* 200)
(setq *list-len*   2000)
(setq *sort-reps*  20)

;;; ---------------------------------------------------------------- tak
(defun tak (x y z)
  (if (not (< y x))
      z
      (tak (tak (- x 1) y z)
           (tak (- y 1) z x)
           (tak (- z 1) x y))))

;;; -------------------------------------------------- symbolic derivative
(defun deriv (a)
  (cond ((atom a) (if (eq a 'x) 1 0))
        ((eq (car a) '+) (cons '+ (mapcar 'deriv (cdr a))))
        ((eq (car a) '-) (cons '- (mapcar 'deriv (cdr a))))
        ((eq (car a) '*)
         (list '* a (cons '+ (mapcar 'deriv-div (cdr a)))))
        ((eq (car a) '/)
         (list '- (list '/ (deriv (cadr a)) (caddr a))
                  (list '/ (cadr a)
                        (list '* (caddr a) (caddr a) (deriv (caddr a))))))
        (t 'error)))

(defun deriv-div (a) (list '/ (deriv a) a))

(setq *deriv-expr*
      '(+ (* 3 x x) (* a x x) (* b x) 5
          (/ (* 2 x) (+ x 1))
          (* (+ x 1) (- x 2) (+ x 3))))

;;; ------------------------------------------------------ list building
(defun iota (n)
  (let ((acc nil))
    (dotimes (i n) (setq acc (cons i acc)))
    acc))

;;; A deliberately naive insertion sort: O(n^2) conses and comparisons, which
;;; is what makes it useful here.
(defun insert (x lst)
  (cond ((null lst) (list x))
        ((< x (car lst)) (cons x lst))
        (t (cons (car lst) (insert x (cdr lst))))))

(defun isort (lst)
  (let ((acc nil))
    (dolist (x lst) (setq acc (insert x acc)))
    acc))

;;; --------------------------------------------------- destructive churn
(defun churn (n)
  (let ((lst (iota n)))
    (dotimes (i 8)
      (setq lst (nreverse lst))
      (setq lst (mapcar '1+ lst)))
    (length lst)))

;;; ------------------------------------------------------------- driver
;;;
;;; One top-level form: the REPL prints the value of each form it reads, and
;;; every printed line is an HTIF round trip to the host.  Keeping the driver
;;; inside a single progn keeps console traffic out of the measurement.
(progn
  (princ "xlisp-bench: tak ") (princ *tak-args*) (princ " = ")
  (princ (apply 'tak *tak-args*)) (terpri)

  (dotimes (i *deriv-reps*) (deriv *deriv-expr*))
  (princ "xlisp-bench: deriv terms = ") (princ (length (deriv *deriv-expr*))) (terpri)

  (dotimes (i *sort-reps*)
    (setq *sorted* (isort (iota *list-len*))))
  (princ "xlisp-bench: isort head = ") (princ (car *sorted*))
  (princ " len = ") (princ (length *sorted*)) (terpri)

  (princ "xlisp-bench: churn = ") (princ (churn *list-len*)) (terpri)
  (princ "xlisp-bench: done") (terpri)
  (exit))
