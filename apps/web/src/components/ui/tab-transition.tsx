import { forwardRef, type ReactNode } from 'react';
import { AnimatePresence, LazyMotion, MotionConfig, m, useIsPresent, useReducedMotion } from 'motion/react';
import { spring } from '@/lib/springs';

const loadFeatures = () => import('@/lib/motion-features').then(module => module.default);

const TabSurface = forwardRef<HTMLDivElement, { children: ReactNode }>(function TabSurface({ children }, ref) {
  const present = useIsPresent();
  const reduceMotion = useReducedMotion();
  return <m.div ref={ref} inert={!present} aria-hidden={!present || undefined}
    initial={{ opacity: 0, transform: reduceMotion ? 'none' : 'translateY(6px)' }}
    animate={{ opacity: 1, transform: reduceMotion ? 'none' : 'translateY(0px)' }}
    exit={{ opacity: 0, transform: reduceMotion ? 'none' : 'translateY(-4px)', transition: spring.moderate.exit }}
    transition={spring.moderate}>
    {children}
  </m.div>;
});

export function TabTransition({ tab, children }: { tab: string; children: ReactNode }) {
  return <MotionConfig reducedMotion="user">
    <LazyMotion features={loadFeatures} strict>
    <div className="laboratory-tab-content" style={{ position: 'relative' }}>
      <AnimatePresence initial={false} mode="popLayout">
        <TabSurface key={tab}>{children}</TabSurface>
      </AnimatePresence>
    </div>
    </LazyMotion>
  </MotionConfig>;
}
