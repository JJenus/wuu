export default defineAppConfig({
  ui: {
    colors: {
      primary: 'teal',
      secondary: 'blue',
      neutral: 'slate',
      success: 'green',
      warning: 'amber',
      error: 'red',
      info: 'blue',
    },
    button: { slots: { base: 'font-medium cursor-pointer' }, defaultVariants: { size: 'md' } },
  },
})
