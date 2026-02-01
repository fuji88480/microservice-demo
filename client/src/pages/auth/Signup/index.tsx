import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@radix-ui/react-label'
import { Link } from 'react-router-dom'

export default function Signup() {
  return (
    <div className="flex justify-center font-mono">
      <div className="space-y-6 p-10 mt-50 rounded-2xl border">
        <div className="space-y-2">
          <h1 className="text-2xl">新規登録</h1>
          <p className="text-sm text-muted-foreground">
            新しいアカウントを作成
          </p>
        </div>

        <form className="space-y-4">
          <div>
            <Label htmlFor="email">E-Mail</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
            />
          </div>

          <div className='mb-10'>
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="********"
            />
          </div>

          <Link to="/auth/signup">
            <Button
              variant="destructive"
              className="w-full bg-pink-700"
            >
              新規登録
            </Button>
          </Link>
        </form>
      </div>
    </div>
  )
}
